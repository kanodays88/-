package com.kanodays88.skytakeoutai.skill;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Skill 文件加载器 —— 扫描 classpath:skills/ 路径下的所有 Skill 定义文件，
 * 解析 YAML frontmatter 和结构化 Sections，将其转换为 {@link Skill} 对象。
 * <p>
 * 文件格式兼容两种规范：
 * <ul>
 *   <li>标准格式（优先）：{@code skills/domain/skill-name/SKILL.md}</li>
 *   <li>旧格式（兜底）：{@code skills/domain/skill-name.md}</li>
 * </ul>
 * <p>
 * 解析策略遵循 Anthropic Agent Skill 标准，支持：
 * <ul>
 *   <li>YAML frontmatter 元数据（name, description, domain, compatibility, metadata）</li>
 *   <li>refrences/ 目录扫描与按需加载</li>
 *   <li>无 YAML frontmatter → 从 {@code # 标题} 或文件名推断 name</li>
 *   <li>Section 名大小写不敏感</li>
 *   <li>表格列名模糊匹配</li>
 *   <li>自由文本兜底 → notes</li>
 *   <li>步骤格式宽容（**bold** 和纯文本两种）</li>
 * </ul>
 */
@Component
@Slf4j
public class SkillLoader {

    /** 匹配 YAML frontmatter：文件首行的 --- 与结束 --- 之间的内容 */
    private static final Pattern FRONTMATTER_PATTERN = Pattern.compile("^---\\s*\\n(.*?)\\n---\\s*\\n", Pattern.DOTALL);

    /** 匹配 ## 二级标题，用于切分 Markdown 的不同 Section */
    private static final Pattern SECTION_PATTERN = Pattern.compile("^##\\s+(.+)$", Pattern.MULTILINE);

    /** 匹配 Execution Flow 中的编号步骤：N. **Name**: Description */
    private static final Pattern NUMBERED_STEP_PATTERN = Pattern.compile("^(\\d+)\\.\\s+\\*\\*(.+?)\\*\\*:\\s*(.+)$", Pattern.MULTILINE);

    /** 匹配自由格式的编号步骤：N. Name Description（无 **bold** 包裹） */
    private static final Pattern PLAIN_STEP_PATTERN = Pattern.compile("^(\\d+)\\.\\s+(.+?)(?::\\s*(.+))?$", Pattern.MULTILINE);

    /** 匹配无序列表项：- item 或 * item */
    private static final Pattern BULLET_PATTERN = Pattern.compile("^\\s*[-*]\\s+(.+)$", Pattern.MULTILINE);

    private final ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

    /**
     * Load all Skill definitions from classpath:skills/ directory.
     * Strategy: prefer standard SKILL.md files, fallback to legacy .md files.
     */
    public List<Skill> loadAllSkills() {
        List<Skill> skills = loadFromPattern("classpath:skills/**/SKILL.md");
        if (skills.isEmpty()) {
            log.info("No SKILL.md files found, falling back to legacy *.md format");
            skills = loadFromPattern("classpath:skills/**/*.md");
        }
        return skills;
    }

    /**
     * 从指定 glob 模式加载所有 Skill 文件。
     */
    private List<Skill> loadFromPattern(String pattern) {
        List<Skill> skills = new ArrayList<>();
        try {
            Resource[] resources = resolver.getResources(pattern);
            for (Resource resource : resources) {
                try {
                    Skill skill = parseSkill(resource);
                    if (skill != null) {
                        skills.add(skill);
                        log.info("Loaded skill: {} (domain: {})", skill.getName(), skill.getDomain());
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse skill file: {}, error: {}", resource.getFilename(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("No skill files found matching pattern: {}", pattern);
        }
        return skills;
    }

    /**
     * 解析单个 Markdown 资源文件为 Skill 对象。
     * <p>
     * 解析流程：
     * <ol>
     *   <li>尝试解析 YAML frontmatter → 获取 name/domain/description/compatibility/metadata</li>
     *   <li>无 frontmatter → 从 body 的 {@code # 标题} 或文件名提取 name</li>
     *   <li>按 {@code ## Section} 将 body 切分成多个 section</li>
     *   <li>逐 section 以结构化方式解析（参数表、步骤等）</li>
     *   <li>body 中未被捕获的「自由文本」→ 存入 notes</li>
     *   <li>扫描同目录下的 references/ → 存入 referencePaths</li>
     * </ol>
     */
    public Skill parseSkill(Resource resource) {
        try {
            String content = readResourceContent(resource);
            if (content == null || content.isBlank()) return null;

            Map<String, Object> frontmatter = parseFrontmatter(content);
            String body = extractBody(content);
            Map<String, String> sections = splitSections(body);

            String name = extractName(frontmatter, body, resource);
            if (name == null) {
                log.warn("Skill file {} has no identifiable name", resource.getFilename());
                return null;
            }

            String domain = extractDomain(frontmatter, resource);

            Skill.SkillBuilder builder = Skill.builder()
                    .name(name)
                    .domain(domain)
                    .description(getStr(frontmatter, "description", ""))
                    .compatibility(getStr(frontmatter, "compatibility", null))
                    .metadata(parseMetadata(frontmatter))
                    .rawContent(content);

            builder.requiredParams(parseParameterTable(findSection(sections, "Required Parameters", "必需参数", "required parameters", "required"), true));
            builder.optionalParams(parseParameterTable(findSection(sections, "Optional Parameters", "可选参数", "optional parameters", "optional"), false));
            builder.steps(parseSteps(findSection(sections, "Execution Flow", "执行流程", "execution flow", "steps", "流程")));
            builder.relatedTools(parseBulletList(findSection(sections, "Related Tools", "关联工具", "related tools", "tools", "工具")));
            builder.examples(parseBulletList(findSection(sections, "Examples", "示例", "examples", "例子")));

            builder.notes(captureNotes(body, sections));
            return builder.build();
        } catch (Exception e) {
            log.error("Error parsing skill from {}: {}", resource.getFilename(), e.getMessage());
            return null;
        }
    }

    /** 以 UTF-8 读取 Resource 的全部文本内容 */
    String readResourceContent(Resource resource) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (Exception e) {
            log.warn("Failed to read resource: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 YAML frontmatter 或文件路径中提取 domain。
     * 优先级：frontmatter.domain > 文件路径中的第一级子目录名 > "general"
     */
    private String extractDomain(Map<String, Object> frontmatter, Resource resource) {
        String domain = getStr(frontmatter, "domain", null);
        if (domain != null && !domain.isBlank()) return domain.trim();

        try {
            String path = resource.getURL().getPath();
            // 从 classpath 路径中提取 domain，如 skills/food_delivery/place_order/SKILL.md
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("skills/([^/]+)").matcher(path);
            if (m.find()) {
                return m.group(1);
            }
        } catch (Exception e) {
            // ignore
        }
        return "general";
    }

    /**
     * 从 YAML frontmatter、body 的一级标题、或文件名中提取技能名称。
     * 优先级：frontmatter.name > body # 标题 > 文件名（不含 .md）
     */
    private String extractName(Map<String, Object> frontmatter, String body, Resource resource) {
        String name = getStr(frontmatter, "name", null);
        if (name != null && !name.isBlank()) return name.trim();

        Matcher headingMatcher = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE).matcher(body);
        if (headingMatcher.find()) {
            return headingMatcher.group(1).trim();
        }

        String filename = resource.getFilename();
        if (filename != null && filename.endsWith(".md")) {
            return filename.substring(0, filename.length() - 3);
        }
        return null;
    }

    /**
     * 解析 Markdown 的 YAML frontmatter（--- 包裹的部分）。
     * 使用 SnakeYAML 将 frontmatter 解析为 Map。
     */
    Map<String, Object> parseFrontmatter(String content) {
        Matcher matcher = FRONTMATTER_PATTERN.matcher(content);
        if (!matcher.find()) return Collections.emptyMap();
        String yamlStr = matcher.group(1);
        try {
            Yaml yaml = new Yaml();
            Map<String, Object> result = yaml.load(yamlStr);
            return result != null ? result : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("Failed to parse YAML frontmatter: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /** 从 Markdown 中移除 YAML frontmatter 部分，只保留 body */
    private String extractBody(String content) {
        return content.replaceFirst("^---\\s*\\n.*?\\n---\\s*\\n", "").trim();
    }

    /** 从 frontmatter 中解析 metadata 自定义键值对 */
    @SuppressWarnings("unchecked")
    private Map<String, String> parseMetadata(Map<String, Object> frontmatter) {
        Object metadataObj = frontmatter.get("metadata");
        if (metadataObj instanceof Map) {
            Map<String, String> result = new HashMap<>();
            ((Map<String, Object>) metadataObj).forEach((k, v) -> result.put(k, v != null ? v.toString() : ""));
            return result;
        }
        return Collections.emptyMap();
    }

    /**
     * 按需加载指定 skill 的参考文档内容。
     * <p>
     * Agent 在执行过程中读到 SKILL.md 正文的参考资料加载规则表后，
     * 由 {@link com.kanodays88.skytakeoutai.tools.LoadReferenceTool} 调用此方法。
     *
     * @param skill         目标 Skill
     * @param referenceFile 参考文档路径（如 "references/api-spec.md"）
     * @return 文件文本内容，加载失败返回 null
     */
    String loadReferenceContent(Skill skill, String referenceFile) {
        if (skill == null || referenceFile == null || referenceFile.isBlank()) return null;
        try {
            String path = "classpath:skills/" + skill.getDomain() + "/" + skill.getName() + "/" + referenceFile;
            Resource resource = resolver.getResource(path);
            if (!resource.exists()) {
                log.warn("Reference file not found: {}", path);
                return null;
            }
            return readResourceContent(resource);
        } catch (Exception e) {
            log.warn("Failed to load reference {}: {}", referenceFile, e.getMessage());
            return null;
        }
    }

    /**
     * 将 body 按 ## 二级标题切分为多个 Section。
     * key 统一转为小写并 trim，以实现大小写不敏感的查找。
     */
    Map<String, String> splitSections(String body) {
        Map<String, String> sections = new java.util.LinkedHashMap<>();
        Matcher matcher = SECTION_PATTERN.matcher(body);
        int lastStart = -1;
        String lastTitle = null;
        while (matcher.find()) {
            if (lastTitle != null) {
                sections.put(lastTitle.toLowerCase(), body.substring(lastStart, matcher.start()).trim());
            }
            lastTitle = matcher.group(1).trim();
            lastStart = matcher.end();
        }
        if (lastTitle != null && lastStart > 0) {
            sections.put(lastTitle.toLowerCase(), body.substring(lastStart).trim());
        }
        return sections;
    }

    /**
     * 从 sections 中模糊匹配查找目标 section 的内容。
     * 匹配策略：全等匹配（忽略大小写）→ contains 匹配。
     */
    private String findSection(Map<String, String> sections, String... possibleKeys) {
        if (sections == null || sections.isEmpty()) return null;

        for (String key : possibleKeys) {
            String lowerKey = key.toLowerCase();
            if (sections.containsKey(lowerKey)) return sections.get(lowerKey);
        }

        for (Map.Entry<String, String> entry : sections.entrySet()) {
            String sectionKey = entry.getKey();
            for (String key : possibleKeys) {
                String lowerKey = key.toLowerCase();
                if (sectionKey.contains(lowerKey) || lowerKey.contains(sectionKey)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }

    /**
     * 捕获 body 中未被任何结构化 sections 匹配到的自由文本。
     * 从 body 中逐个移除所有已知 section 的标题行及其内容，剩余部分即为 notes。
     */
    private String captureNotes(String body, Map<String, String> sections) {
        if (body == null || body.isBlank()) return "";
        if (sections == null || sections.isEmpty()) return body;

        String remainder = body;
        Matcher matcher = SECTION_PATTERN.matcher(remainder);
        while (matcher.find()) {
            String sectionTitle = matcher.group(1).trim();
            String lowerTitle = sectionTitle.toLowerCase();
            if (sections.containsKey(lowerTitle)) {
                String sectionContent = sections.get(lowerTitle);
                remainder = remainder.replace("## " + sectionTitle + "\n" + sectionContent, "");
                remainder = remainder.replace("## " + sectionTitle, "");
            }
        }
        remainder = remainder.trim();
        return remainder.isBlank() ? "" : remainder;
    }

    /** 解析 Markdown 参数表格为 SkillParameter 列表 */
    List<SkillParameter> parseParameterTable(String sectionContent, boolean required) {
        if (sectionContent == null || sectionContent.isBlank()) return new ArrayList<>();
        List<SkillParameter> params = new ArrayList<>();
        String[] lines = sectionContent.split("\n");

        if (lines.length < 2) return params;

        String[] headers = parseTableRow(lines[0]);
        if (headers.length < 2) return params;

        int nameCol = -1;
        int typeCol = -1;
        int descCol = -1;
        for (int i = 0; i < headers.length; i++) {
            String h = headers[i].trim().toLowerCase();
            if (nameCol < 0 && (h.contains("名") || h.contains("name"))) nameCol = i;
            if (typeCol < 0 && (h.contains("类型") || h.contains("type"))) typeCol = i;
            if (descCol < 0 && (h.contains("说明") || h.contains("desc") || h.contains("描述"))) descCol = i;
        }
        if (nameCol < 0) nameCol = 0;

        for (int i = 2; i < lines.length; i++) {
            String[] cells = parseTableRow(lines[i]);
            if (cells.length <= nameCol) continue;
            SkillParameter param = new SkillParameter();
            param.setName(cells[nameCol].trim());
            param.setType(typeCol >= 0 && cells.length > typeCol ? cells[typeCol].trim() : "string");
            param.setDescription(descCol >= 0 && cells.length > descCol ? cells[descCol].trim() : "");
            param.setRequired(required);
            params.add(param);
        }
        return params;
    }

    /** 解析 Markdown 表格的一行 */
    private String[] parseTableRow(String line) {
        String trimmed = line.trim();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|")) trimmed = trimmed.substring(0, trimmed.length() - 1);
        return trimmed.split("\\|");
    }

    /** 解析 Execution Flow 的步骤列表，同时支持 **bold** 和纯文本两种格式 */
    List<SkillStep> parseSteps(String sectionContent) {
        if (sectionContent == null || sectionContent.isBlank()) return new ArrayList<>();
        List<SkillStep> steps = new ArrayList<>();
        java.util.Set<Integer> seenOrders = new java.util.HashSet<>();

        Matcher strictMatcher = NUMBERED_STEP_PATTERN.matcher(sectionContent);
        while (strictMatcher.find()) {
            int order = Integer.parseInt(strictMatcher.group(1));
            SkillStep step = new SkillStep();
            step.setOrder(order);
            step.setName(strictMatcher.group(2).trim());
            step.setDescription(strictMatcher.group(3).trim());
            step.setTools(extractToolNames(strictMatcher.group(3)));
            steps.add(step);
            seenOrders.add(order);
        }

        Matcher plainMatcher = PLAIN_STEP_PATTERN.matcher(sectionContent);
        while (plainMatcher.find()) {
            int order = Integer.parseInt(plainMatcher.group(1));
            if (seenOrders.contains(order)) continue;
            SkillStep step = new SkillStep();
            step.setOrder(order);
            step.setName(plainMatcher.group(2).trim());
            step.setDescription(plainMatcher.group(3) != null ? plainMatcher.group(3).trim() : "");
            step.setTools(extractToolNames(plainMatcher.group(2) + " " + (plainMatcher.group(3) != null ? plainMatcher.group(3) : "")));
            steps.add(step);
            seenOrders.add(order);
        }

        steps.sort((a, b) -> Integer.compare(a.getOrder(), b.getOrder()));
        return steps;
    }

    /** 从文本中提取以 "Tool" 结尾的单词作为步骤关联的工具名 */
    List<String> extractToolNames(String text) {
        List<String> tools = new ArrayList<>();
        if (text == null) return tools;
        Matcher matcher = Pattern.compile("\\b(\\w+Tool)\\b").matcher(text);
        while (matcher.find()) {
            tools.add(matcher.group(1));
        }
        return tools;
    }

    /** 解析无序列表（- 或 * 开头），返回列表项内容 */
    List<String> parseBulletList(String sectionContent) {
        if (sectionContent == null || sectionContent.isBlank()) return new ArrayList<>();
        List<String> items = new ArrayList<>();
        Matcher matcher = BULLET_PATTERN.matcher(sectionContent);
        while (matcher.find()) {
            items.add(matcher.group(1).trim());
        }
        return items;
    }

    /** 从 Map 中安全获取 String 类型的值 */
    private String getStr(Map<String, Object> map, String key, String defaultValue) {
        if (map == null) return defaultValue;
        Object val = map.get(key);
        return val != null ? val.toString() : defaultValue;
    }
}

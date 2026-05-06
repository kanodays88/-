package com.kanodays88.skytakeoutai.skill;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Skill 注册中心 —— 管理所有已加载的业务技能，提供查找和上下文生成能力。
 * <p>
 * 在应用启动时通过 {@link SkillLoader#loadAllSkills()} 加载
 * {@code classpath:skills/**\/*.md} 下的所有技能定义，缓存到内存中。
 * <p>
 * 架构中的两处使用点：
 * <ul>
 *   <li><b>RouterAgent</b>：调用 {@link #findRelevant(String)} 匹配用户输入对应的 Skill，
 *       结合 Skill 的必需参数判断用户信息是否充分 → 决定是否 AMBIGUOUS</li>
 *   <li><b>PlanExecute</b>：调用 {@link #getMatchingSkillsPromptContext(String)} 生成
 *       匹配 Skill 的 Markdown 上下文文本 → 注入 LLM system prompt 指导任务分解</li>
 * </ul>
 */
@Component
@Slf4j
public class SkillRegistry {

    private final SkillLoader skillLoader;

    /** 所有已加载的 Skill 列表 */
    @Getter
    private List<Skill> allSkills = new ArrayList<>();

    /** Skill 名称 → Skill 的映射，用于快速精确查找 */
    private Map<String, Skill> skillMap = new HashMap<>();

    public SkillRegistry(SkillLoader skillLoader) {
        this.skillLoader = skillLoader;
    }

    /** 应用启动时自动加载所有 Skill 文件 */
    @PostConstruct
    public void init() {
        allSkills = skillLoader.loadAllSkills();
        skillMap = allSkills.stream()
                .collect(Collectors.toMap(Skill::getName, s -> s, (a, b) -> a));
        log.info("SkillRegistry initialized with {} skills", allSkills.size());
        if (allSkills.isEmpty()) {
            log.warn("No skills loaded. Check resources/skills/ directory.");
        }
    }

    /** 根据技能名称精确查找 */
    public Skill getSkill(String name) {
        return skillMap.get(name);
    }

    /** 根据业务领域查找第一个匹配的技能 */
    public Skill getSkillByDomain(String domain) {
        return allSkills.stream()
                .filter(s -> domain.equals(s.getDomain()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据用户输入查询匹配的 Skill 列表，按相关度降序排列。
     * <p>
     * 匹配维度（按权重从高到低）：
     * <ol>
     *   <li>用户输入包含 Skill 名称</li>
     *   <li>用户输入包含 Skill 所属领域名</li>
     *   <li>用户输入中的关键词与 Skill 描述匹配</li>
     *   <li>用户输入中的关键词与 Skill 示例匹配</li>
     *   <li>用户输入包含 Skill 参数名</li>
     * </ol>
     * 如果没有匹配到任何 Skill，返回空列表。
     */
    public List<Skill> findRelevant(String query) {
        if (query == null || query.isBlank()) return new ArrayList<>(allSkills);

        String lowerQuery = query.toLowerCase();
        Set<Skill> matched = new LinkedHashSet<>();

        for (Skill skill : allSkills) {
            double score = calculateRelevance(skill, lowerQuery);
            if (score > 0) {
                matched.add(skill);
            }
        }

        if (matched.isEmpty()) {
            return new ArrayList<>();
        }

        List<Skill> sorted = new ArrayList<>(matched);
        sorted.sort((a, b) -> Double.compare(
                calculateRelevance(b, lowerQuery),
                calculateRelevance(a, lowerQuery)));
        return sorted;
    }

    /** 将所有 Skill 格式化为 LLM prompt 上下文文本 */
    public String getSkillsPromptContext() {
        return allSkills.stream()
                .map(Skill::toPromptContext)
                .collect(Collectors.joining("\n---\n"));
    }

    /** 将指定的 Skill 列表格式化为 LLM prompt 上下文文本 */
    public String getSkillsPromptContext(List<Skill> skills) {
        return skills.stream()
                .map(Skill::toPromptContext)
                .collect(Collectors.joining("\n---\n"));
    }

    /**
     * 根据用户输入生成匹配 Skill 的 prompt 上下文文本。
     * 先通过 {@link #findRelevant(String)} 匹配，然后将匹配到的 Skill
     * 格式化为 Markdown 文本，用于注入 LLM 的 system prompt。
     * <p>
     * 输出格式：先列出匹配到的 Skills 概览，再给出每个 Skill 的详细定义。
     */
    public String getMatchingSkillsPromptContext(String query) {
        List<Skill> relevant = findRelevant(query);
        if (relevant.isEmpty()) return "当前没有匹配的业务技能。";
        return "当前可用的业务技能：\n" + relevant.stream()
                .map(s -> "- " + s.getName() + ": " + s.getDescription())
                .collect(Collectors.joining("\n")) + "\n\n详细定义：\n" + getSkillsPromptContext(relevant);
    }

    /**
     * 按需加载指定技能的参考文档内容 —— 执行层（Execution）的核心入口。
     * <p>
     * 对应 Anthropic Agent Skill 标准第三层「执行层」：仅在 Agent 执行过程中
     * 引用对应资源时才加载文件内容，最大化节约上下文 Token。
     *
     * @param skillName     技能名称
     * @param referenceFile 参考文档路径（如 "references/api-spec.md"）
     * @return 文件文本内容，加载失败返回 null
     */
    public String getReferenceContent(String skillName, String referenceFile) {
        Skill skill = getSkill(skillName);
        if (skill == null || referenceFile == null) return null;
        return skillLoader.loadReferenceContent(skill, referenceFile);
    }

    /**
     * 计算单个 Skill 与用户查询的相关度分数。
     * 多维度加权求和，权重从高到低：名称匹配 &gt; 领域匹配 &gt; 描述关键词 &gt; 示例关键词 &gt; 参数名。
     */
    private double calculateRelevance(Skill skill, String lowerQuery) {
        double score = 0.0;

        if (skill.getName() != null && lowerQuery.contains(skill.getName().toLowerCase())) {
            score += 1.0;
        }

        if (skill.getDomain() != null && lowerQuery.contains(skill.getDomain().toLowerCase())) {
            score += 0.8;
        }

        if (skill.getDescription() != null) {
            String lowerDesc = skill.getDescription().toLowerCase();
            for (String word : lowerQuery.split("\\s+")) {
                if (word.length() > 1 && lowerDesc.contains(word)) {
                    score += 0.3;
                }
            }
        }

        if (skill.getExamples() != null) {
            for (String ex : skill.getExamples()) {
                String lowerEx = ex.toLowerCase();
                long matchCount = Arrays.stream(lowerQuery.split("\\s+"))
                        .filter(w -> w.length() > 1 && lowerEx.contains(w))
                        .count();
                score += matchCount * 0.2;
            }
        }

        if (skill.getAllParamNames() != null) {
            for (String paramName : skill.getAllParamNames()) {
                if (lowerQuery.contains(paramName.toLowerCase())) {
                    score += 0.1;
                }
            }
        }

        return score;
    }
}

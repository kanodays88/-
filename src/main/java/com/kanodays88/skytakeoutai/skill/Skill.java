package com.kanodays88.skytakeoutai.skill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 业务技能 —— 描述一个完整的业务流程所需的信息和执行步骤。
 * <p>
 * 每个 Skill 对应一个独立的 Markdown 定义文件（如 place_order.md），
 * 由 {@link SkillLoader} 在应用启动时解析并加载到 {@link SkillRegistry} 中。
 * <p>
 * Skill 在架构中作为「LLM 的参考上下文」使用：
 * <ul>
 *   <li>RouterAgent 将 Skill 的必需参数列表注入路由 prompt，辅助判断用户输入是否完整 → AMBIGUOUS</li>
 *   <li>PlanExecute 将 Skill 的执行流程注入任务分解 prompt，指导子任务的生成顺序和关注点</li>
 * </ul>
 * <p>
 * 为兼容用户自定义 Skill，所有结构化字段（参数表、步骤等）均可能为空，
 * 此时 {@link #toPromptContext()} 会回退输出 {@link #notes} 或 {@link #rawContent}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    /** 技能唯一标识名，如 "place_order"、"my_custom_skill" */
    private String name;

    /** 所属业务领域，如 "food_delivery"；用户未指定时默认为 "general" */
    private String domain;

    /** 技能描述，简要说明该技能的作用 */
    private String description;

    /** 必需参数列表 —— LLM 判断用户信息是否充分的依据 */
    private List<SkillParameter> requiredParams;

    /** 可选参数列表 */
    private List<SkillParameter> optionalParams;

    /** 执行步骤列表，描述按序执行的业务流程 */
    private List<SkillStep> steps;

    /** 关联的工具名列表 */
    private List<String> relatedTools;

    /** 用户提问示例列表，用于辅助 LLM 匹配用户意图 */
    private List<String> examples;

    /**
     * 未被结构化 sections 捕获的自由文本 —— 兼容用户写的非严格格式。
     * 包含执行流程描述、注意事项、不在规定 sections 中的任何内容。
     * 当所有结构化字段都为空时，toPromptContext() 会回退到此字段。
     */
    private String notes;

    /** 原始 Markdown 全文，可在必要时直接注入 LLM 以保留完整格式 */
    private String rawContent;

    /** 兼容性/前置条件说明 —— 标注环境、依赖包、网络权限等运行前置条件 */
    private String compatibility;

    /** 自定义元数据 —— 用于扩展作者、版本、标签、分类等额外信息 */
    private Map<String, String> metadata;

    /**
     * 将 Skill 格式化为 LLM 可读的 prompt 上下文文本。
     * <p>
     * 优先级：结构化字段（参数表、流程、工具等）→ notes → rawContent。
     * 当结构化内容全部为空时自动回退到 notes 或 rawContent，
     * 确保用户写的非严格格式也能被 LLM 理解。
     */
    public String toPromptContext() {
        StringBuilder sb = new StringBuilder();
        sb.append("## Skill: ").append(name).append("\n");
        sb.append("- Domain: ").append(domain).append("\n");
        sb.append("- Description: ").append(description != null ? description : "").append("\n");
        if (compatibility != null && !compatibility.isBlank()) {
            sb.append("- Compatibility: ").append(compatibility).append("\n");
        }
        if (metadata != null && !metadata.isEmpty()) {
            sb.append("- Metadata: ").append(metadata).append("\n");
        }
        sb.append("\n");

        boolean hasStructured = false;

        if (requiredParams != null && !requiredParams.isEmpty()) {
            hasStructured = true;
            sb.append("### Required Parameters\n");
            sb.append("| 参数名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");
            for (SkillParameter p : requiredParams) {
                sb.append("| ").append(p.getName()).append(" | ").append(p.getType()).append(" | ").append(p.getDescription()).append(" |\n");
            }
            sb.append("\n");
        }

        if (optionalParams != null && !optionalParams.isEmpty()) {
            hasStructured = true;
            sb.append("### Optional Parameters\n");
            sb.append("| 参数名 | 类型 | 说明 |\n");
            sb.append("|--------|------|------|\n");
            for (SkillParameter p : optionalParams) {
                sb.append("| ").append(p.getName()).append(" | ").append(p.getType()).append(" | ").append(p.getDescription()).append(" |\n");
            }
            sb.append("\n");
        }

        if (steps != null && !steps.isEmpty()) {
            hasStructured = true;
            sb.append("### Execution Flow\n");
            for (SkillStep step : steps) {
                sb.append(step.getOrder()).append(". **").append(step.getName()).append("**: ").append(step.getDescription());
                if (step.getTools() != null && !step.getTools().isEmpty()) {
                    sb.append(" (tools: ").append(String.join(", ", step.getTools())).append(")");
                }
                sb.append("\n");
            }
            sb.append("\n");
        }

        if (relatedTools != null && !relatedTools.isEmpty()) {
            hasStructured = true;
            sb.append("### Related Tools\n");
            sb.append(String.join(", ", relatedTools)).append("\n\n");
        }

        if (examples != null && !examples.isEmpty()) {
            hasStructured = true;
            sb.append("### Examples\n");
            for (String ex : examples) {
                sb.append("- ").append(ex).append("\n");
            }
            sb.append("\n");
        }

        if (!hasStructured) {
            if (notes != null && !notes.isBlank()) {
                sb.append("### Notes\n").append(notes).append("\n\n");
            } else if (rawContent != null && !rawContent.isBlank()) {
                sb.append("### Raw Content\n").append(rawContent).append("\n\n");
            }
        } else if (notes != null && !notes.isBlank()) {
            sb.append("### Notes\n").append(notes).append("\n\n");
        }

        return sb.toString();
    }

    /**
     * 获取所有必需参数的名称列表。
     * 用于 RouterAgent 快速判断用户输入中缺失了哪些必要信息。
     */
    public List<String> getAllRequiredParamNames() {
        if (requiredParams == null) return List.of();
        return requiredParams.stream().map(SkillParameter::getName).collect(Collectors.toList());
    }

    /**
     * 获取所有参数（必需 + 可选）的名称列表。
     */
    public List<String> getAllParamNames() {
        java.util.List<String> names = new java.util.ArrayList<>();
        if (requiredParams != null) requiredParams.forEach(p -> names.add(p.getName()));
        if (optionalParams != null) optionalParams.forEach(p -> names.add(p.getName()));
        return names;
    }
}

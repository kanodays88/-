package com.kanodays88.skytakeoutai.tools;

import com.kanodays88.skytakeoutai.skill.SkillRegistry;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * Agent 执行层按需加载参考文档的工具。
 * <p>
 * 对应 Anthropic Agent Skill 标准「三层加载」中的执行层（Execution）：
 * Agent 在读取 SKILL.md 正文的参考资料加载规则表后，自行判断是否需要加载对应参考文件。
 * 当匹配到触发条件时，Agent 调用此工具来加载文件内容。
 * <p>
 * 使用示例（由 Agent 自主触发）：
 * <pre>
 * load_reference(
 *   skillName = "place_order",
 *   referenceFile = "references/domestic-travel-reimbursement-standards.md"
 * )
 * </pre>
 */
@Component
public class LoadReferenceTool {

    private final SkillRegistry skillRegistry;

    public LoadReferenceTool(SkillRegistry skillRegistry) {
        this.skillRegistry = skillRegistry;
    }

    /**
     * 按需加载当前匹配到的业务技能的参考文档。
     * 仅当用户请求匹配 SKILL.md 中参考资料加载规则表的触发条件时才调用此工具。
     * 调用前请确认：skillName 正确、referenceFile 路径与 SKILL.md 中一致。
     *
     * @param skillName     当前激活的业务技能名称，如 place_order、cancel_order
     * @param referenceFile 参考文档路径，如 references/domestic-travel-reimbursement-standards.md
     * @return 参考文件的内容文本
     */
    @Tool(description = "按需加载当前业务技能的参考文档。" +
            "仅当用户请求匹配 SKILL.md 中参考资料加载规则表的触发条件时才调用此工具。")
    public String loadReference(
            @ToolParam(description = "当前激活的业务技能名称，如 place_order、cancel_order") String skillName,
            @ToolParam(description = "参考文档路径，如 references/domestic-travel-reimbursement-standards.md") String referenceFile
    ) {
        String content = skillRegistry.getReferenceContent(skillName, referenceFile);
        if (content == null) {
            return "错误：参考文件 " + referenceFile + " 未找到，请确认路径是否正确。";
        }
        return content;
    }
}

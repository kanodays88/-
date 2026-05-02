package com.kanodays88.skytakeoutai.agent.plan;


import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.agent.Kanodays88Manus;
import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import com.kanodays88.skytakeoutai.common.ChatSystem;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

// 1. 任务结构化模型（对应 OpenManus 的意图解析输出）
record TaskSchema(
        String mainGoal,        // 核心目标
        String constraints,     // 约束条件
        String deliverables     // 交付要求
) {}

/**
 * 2. 子任务契约：核心解决「蒸馏不丢下游信息」的关键
 */
record SubTask(
        // 子任务序号（全局唯一）
        int taskId,
        // 子任务名称
        String taskName,
        // 子任务执行内容
        String taskContent,
        // 【核心】所有依赖该子任务的下游任务ID（不止紧邻的下一个）
        Set<Integer> downstreamTaskIds,
        // 【核心】所有下游任务要求必须输出的字段（蒸馏时绝对不能删）
        Set<String> requiredFields,
        // 【核心】子任务输出的JSON Schema（强制结构化蒸馏）
        String outputSchema,
        // 该任务需要调用的工具
        Set<String> toolName
) {}

/**
 * 3. 任务分解结果：带全局约束的子任务列表
 */
record DecomposedTasks(
        // 【核心】全局强制留存字段（所有子任务蒸馏都不能删）
        Set<String> globalRequiredFields,
        // 带契约的子任务列表
        List<SubTask> subTaskList
) {}

/**
 * 4. 蒸馏结果双副本：解决上下文膨胀+兜底召回
 */
record DistilledResult(
        // 所属子任务ID
        int taskId,
        // 【进上下文】结构化蒸馏后的核心结果（token压缩90%+）
        String structuredCoreResult,
        // 【归档不进上下文】子任务原始完整结果（兜底召回用）
        String rawResult,
        // 子任务契约（用于下游校验）
        SubTask subTask
) {}




@Component
@Slf4j
public class PlanExecute {

    private ChatClient chatClient;

    private FileBasedChatMemory fileBasedChatMemory;

    private OpenAiChatModel openAiChatModel;

    @Autowired
    private SSESend sseSend;

    @Autowired
    private ToolCallback[] allTools;

    public PlanExecute(OpenAiChatModel openAiChatModel){
        this.openAiChatModel = openAiChatModel;
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultSystem(ChatSystem.CHAT_SYSTEM)
                .defaultAdvisors(
                new MyLoggerAdvisor()
        ).build();
        this.fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "chatMemory");
    }
    //计划执行，整个智能体执行的入口
    public SseEmitter planExecute(String userPrompt,String conversationId){
        // 1. 创建SSE发射器，设置10分钟超时（根据任务复杂度调整）
        SseEmitter emitter = new SseEmitter(10 * 60 * 1000L);
        //异步执行智能体
        CompletableFuture.runAsync(()->{
            try{
                //获取当前会话最近10条消息记录
                List<Message> messages = fileBasedChatMemory.get(conversationId);
                fileBasedChatMemory.add(conversationId,List.of(new UserMessage(userPrompt)));//将用户输入存记忆
                //意图分析
                if(!sseSend.sendEventThink(emitter,"开始进行意图分析...\n")) return;
                TaskSchema taskSchema = parseIntent(userPrompt,messages);
                String taskSchemaMessage = "意图解析完成\n";
                if(!taskSchema.mainGoal().equals("") && !taskSchema.mainGoal().isEmpty()) taskSchemaMessage += "核心目标: "+taskSchema.mainGoal()+"\n";
                if(!taskSchema.deliverables().equals("") && !taskSchema.deliverables().isEmpty()) taskSchemaMessage += "交付要求: "+taskSchema.deliverables()+"\n";
                if(!taskSchema.constraints().equals("") && !taskSchema.constraints().isEmpty()) taskSchemaMessage += "约束条件: "+taskSchema.constraints()+"\n";
                if(!sseSend.sendEventThink(emitter,taskSchemaMessage)) return;

                //对意图进行任务拆分
                if(!sseSend.sendEventThink(emitter,"开始对任务进行拆分...\n")) return;
                DecomposedTasks decomposedTasks = decomposeTaskWithContract(taskSchema);
                List<SubTask> subTasks = decomposedTasks.subTaskList();
                String taskMessage = subTasks.stream().map(s -> {
                    return "任务" + s.taskId() + "：" + s.taskName();
                }).collect(Collectors.joining("\n---\n"));
                if(!sseSend.sendEventThink(emitter,"任务拆分完成：\n"+taskMessage)) return;
                //对每个子任务执行，得到结果集
                List<DistilledResult> results = new ArrayList<>();
                for(SubTask task:subTasks){
                    //获得该任务需要使用的工具集
                    ToolCallback[] tools = getTools(task.toolName());
                    //初始化执行该任务的智能体
                    Kanodays88Manus kanodays88Manus = new Kanodays88Manus(tools, openAiChatModel);
                    if(!sseSend.sendEventThink(emitter,"开始执行任务【"+task.taskName()+"】\n")) return;
                    //获取该任务对应所需的上游任务的结果
                    String upStreamTaskResult = checkAndFillUpstreamContext(task, results);
                    //将上游的结果作为记忆输入给智能体
                    kanodays88Manus.setMessageList(List.of(upStreamTaskResult).stream().map(s->new SystemMessage(s)).collect(Collectors.toList()));

                    //执行任务，得到本次任务的原始结果
                    List<String> childResult = kanodays88Manus.run(task.taskContent(),task.taskName(),emitter,sseSend);
                    //原始结果拼接
                    String result = childResult.stream().collect(Collectors.joining("/n---/n"));
                    //蒸馏任务结果
                    DistilledResult distilledResult = distillSubTaskResult(task, result, decomposedTasks.globalRequiredFields());

                    results.add(distilledResult);
                }

                //整合结果集和意图，得到最终结果
                String s = fuseResults(taskSchema, results);
                fileBasedChatMemory.add(conversationId,List.of(new AssistantMessage(s)));//将模型返回的最终结果存入记忆
                if(!sseSend.sendEventResult(emitter,s)) return;
                //关闭链接
                emitter.complete();
            }catch (Exception e){
                log.error("PlanExecute 执行失败", e.getMessage());
                sseSend.sendEventResult(emitter, "执行失败: " + e.getMessage());
                emitter.completeWithError(e);
            }finally {
                emitter.complete();
            }
        });
        return emitter;
    }
    //从所需工具名称集合中获取到具体工具集合
    public ToolCallback[] getTools(Set<String> toolNames){
        ToolCallback[] toolCallbacks = Arrays.stream(allTools).filter(t -> (toolNames.contains(t.getToolDefinition().name())||t.getToolDefinition().name().equals("assignmentFinish"))).toArray(ToolCallback[]::new);
        return toolCallbacks;
    }

    //意图解析
    public TaskSchema parseIntent(String userPrompt,List<Message> messages){
        Prompt historyPrompt = new Prompt(messages);
        //结构化输出转换器，能将大模型输出转换成对应类型
        BeanOutputConverter<TaskSchema> converter = new BeanOutputConverter<>(TaskSchema.class);
        String prompt = """
                    分析用户的需求，提取核心目标、约束条件、交付要求，以 JSON 格式返回。
                    【输出格式要求】: {format}
                """;
        TaskSchema taskSchema = chatClient.prompt(historyPrompt).system(
                        s -> s.text(prompt).
                                param("format", converter.getJsonSchema()))
                .user(userPrompt)
                .call()
                .entity(converter);
        return taskSchema;
    }
    //任务分解
    public DecomposedTasks decomposeTaskWithContract(TaskSchema taskSchema) {
        BeanOutputConverter<DecomposedTasks> converter = new BeanOutputConverter<>(DecomposedTasks.class);
        String prompt = """
            ##根据任务复杂度拆解为合适数量的子任务（0到5个）：
            - 1个：任务极简单（如询问时间、打招呼等）
            - 2个：中等复杂度任务
            - 3到5个：复杂任务，按执行顺序拆分
            ##任务生成规则
            生成带依赖契约的结构化结果，严格遵守规则：
            1. 先提取【全局强制留存字段】：基于用户的交付要求，提取所有最终交付必须用到的核心字段，所有子任务蒸馏时绝对不能删除；
            2. 为每个子任务定义完整契约：
               - taskId：子任务序号，从1开始递增
               - taskName：子任务名称
               - taskContent：子任务具体执行内容
               - downstreamTaskIds：所有会用到该子任务结果的下游任务的taskId（不止紧邻的下一个任务，必须覆盖所有下游依赖）
               - requiredFields：所有下游任务要求该子任务必须输出的字段，蒸馏时绝对不能删除
               - outputSchema：子任务输出结果的JSON Schema，必须包含所有requiredFields
               - tools：子任务执行需要调用的工具
            3. 子任务依赖关系必须清晰，确保所有下游需要的字段都被提前定义，不能出现下游需要的字段上游没输出的情况。

            输出格式要求：{format}
            """;
        String userPrompt = """
                    核心目标：{mainGoal}
                    约束条件：{constraints}
                    交付要求：{deliverables}
                """;

        return chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("format", converter.getJsonSchema()))
                .user(u -> u.text(userPrompt)
                        .param("mainGoal",taskSchema.mainGoal())
                        .param("constraints",taskSchema.constraints())
                        .param("deliverables",taskSchema.deliverables()))
                .toolCallbacks(allTools)
                .call()
                .entity(converter);
    }

    /**
     * 选取上游任务的蒸馏后结果，若有问题则提取原结果重新蒸馏
     * @param currentTask 当前任务
     * @param upstreamResults  上游任务列表
     * @return
     */
    private String checkAndFillUpstreamContext(SubTask currentTask, List<DistilledResult> upstreamResults) {
        StringBuilder context = new StringBuilder();
        // 没有上游依赖，直接返回空
        if (upstreamResults.isEmpty()) return "";

        // 遍历所有上游结果，校验当前任务需要的字段是否完整
        for (DistilledResult upstreamResult : upstreamResults) {
            // 只处理当前任务依赖的上游任务
            if (!upstreamResult.subTask().downstreamTaskIds().contains(currentTask.taskId())) continue;

            SubTask upstreamTask = upstreamResult.subTask();//获取上游任务
            Set<String> requiredFields = upstreamTask.requiredFields();
            String coreResult = upstreamResult.structuredCoreResult();

            // 把校验后的上游核心结果加入上下文
            context.append("上游任务:").append(upstreamTask.taskContent()).append("\n核心结果:").append(coreResult).append("\n---\n");
        }
        return context.toString();
    }

    /**
     * 任务蒸馏
     * @param subTask 原任务
     * @param rawResult 原任务返回结果
     * @param globalRequiredFields 全局任务必须要求的字段
     * @return
     */
    private DistilledResult distillSubTaskResult(SubTask subTask, String rawResult, Set<String> globalRequiredFields) {
        String prompt = """
            对用户输入的子任务的原始结果做定向蒸馏，严格遵守以下规则，违规直接输出无效：
            1. 必须严格按照输出Schema输出纯JSON
            2. 必须完整留存下游任务需要的所有字段：{requiredFields}
            3. 全局强制留存字段，绝对不能删除：{globalRequiredFields}
            4. 仅可删除冗余推理过程、无关描述、重复话术，不得修改任何核心数据；
            5. 输出必须是纯JSON，不能有任何额外的解释、markdown格式、代码块标记。

            输出Schema：{outputSchema}
            """;
        String userPrompt = """
                子任务名称：{taskName}
                子任务原始结果：{rawResult}
                """;

        // 结构化蒸馏，强制符合Schema，保证必填字段不丢
        String structuredCoreResult = chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("requiredFields", subTask.requiredFields())
                        .param("globalRequiredFields", globalRequiredFields)
                        .param("outputSchema",subTask.outputSchema()))
                .user(u->u.text(userPrompt).param("taskName",subTask.taskName()).param("rawResult",rawResult))
                .call()
                .content();

        // --- 新增校验降级逻辑 ---
        String validatedResult;
        try {
            // 尝试清洗并解析 JSON
            String cleaned = structuredCoreResult
                    .replaceAll("```(?:json)?", "")  // 去除代码块标记
                    .trim();
            new ObjectMapper().readTree(cleaned);    // 仅校验合法性，不反序列化
            validatedResult = cleaned;
        } catch (Exception e) {
            log.warn("蒸馏结果 JSON 校验失败，降级使用原始包装, task={}", subTask.taskName());
            // 降级方案：将原始结果包裹在简单 JSON 中，确保下游不会收到纯文本
            validatedResult = String.format(
                    "{\"taskName\":\"%s\",\"rawFallback\":%s}",
                    subTask.taskName(),
                    JSONUtil.toJsonStr(rawResult)  // 需确保转义
            );
        }
        return new DistilledResult(subTask.taskId(), validatedResult, rawResult, subTask);
    }

    //整合结果集和意图，得到最终结果
    private String fuseResults(TaskSchema task, List<DistilledResult> subTaskResults) {
        List<String> results = subTaskResults.stream().map(s -> s.structuredCoreResult()).collect(Collectors.toList());
        String prompt = """
            角色设定：{System}
            基于以下子任务结果，整合成最终结果报告返回给用户
            原始核心目标: {mainGoal}
            交付要求: {deliverables}
            子任务结果列表: {subTaskResults}
            """;

        return chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("System",ChatSystem.CHAT_SYSTEM)
                        .param("mainGoal", task.mainGoal())
                        .param("deliverables", task.deliverables())
                        .param("subTaskResults", String.join("\n---\n", results)))
                .call()
                .content();
    }

}



















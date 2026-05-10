package com.kanodays88.skytakeoutai.agent.plan;


import cn.hutool.json.JSONUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kanodays88.skytakeoutai.advisor.MyLoggerAdvisor;
import com.kanodays88.skytakeoutai.agent.Kanodays88Manus;

import com.kanodays88.skytakeoutai.agent.sse.SSESend;
import com.kanodays88.skytakeoutai.constant.FileConstant;
import com.kanodays88.skytakeoutai.content.BaseContent;
import com.kanodays88.skytakeoutai.memory.FileBasedChatMemory;
import com.kanodays88.skytakeoutai.skill.Skill;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;


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
    private ToolCallback[] allTools;

    private final Object sseLock = new Object();

    public PlanExecute(OpenAiChatModel openAiChatModel){
        this.openAiChatModel = openAiChatModel;
        this.chatClient = ChatClient.builder(openAiChatModel)
                .defaultAdvisors(
                new MyLoggerAdvisor()
        ).build();
        this.fileBasedChatMemory = new FileBasedChatMemory(FileConstant.FILE_SAVE_DIR + "/chatMemory");
    }
    //计划执行，整个智能体执行的入口
    public String planExecute(String originalTask, String conversationId, SseEmitter emitter){
        long overallStart = System.currentTimeMillis();
//        //获取当前主线程的上下文
//        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
//        //异步执行智能体
//        CompletableFuture.runAsync(()->{
//            try{
//                //将主线程上下文设置到子线程中
//                if (attributes != null) {
//                    RequestContextHolder.setRequestAttributes(attributes);
//                }
//
////                //获取当前会话最近10条消息记录
////                List<Message> messages = fileBasedChatMemory.get(conversationId);
////                fileBasedChatMemory.add(conversationId,List.of(new UserMessage(userPrompt)));//将用户输入存记忆
////                //意图分析
////                if(!sseSend.sendEventThink(emitter,"开始进行意图分析...\n")) return;
////                TaskSchema taskSchema = parseIntent(userPrompt,messages);
////                String taskSchemaMessage = "意图解析完成\n";
////                if(!taskSchema.mainGoal().equals("") && !taskSchema.mainGoal().isEmpty()) taskSchemaMessage += "核心目标: "+taskSchema.mainGoal()+"\n";
////                if(!taskSchema.deliverables().equals("") && !taskSchema.deliverables().isEmpty()) taskSchemaMessage += "交付要求: "+taskSchema.deliverables()+"\n";
////                if(!taskSchema.constraints().equals("") && !taskSchema.constraints().isEmpty()) taskSchemaMessage += "约束条件: "+taskSchema.constraints()+"\n";
////                if(!sseSend.sendEventThink(emitter,taskSchemaMessage)) return;
//
//                //对意图进行任务拆分
//                if(!sseSend.sendEventThink(emitter,"开始对任务进行拆分...\n")) return;
//                DecomposedTasks decomposedTasks = decomposeTaskWithContract(originalTask);
//                List<SubTask> subTasks = decomposedTasks.subTaskList();
//                String taskMessage = subTasks.stream().map(s -> {
//                    return "任务" + s.taskId() + "：" + s.taskName();
//                }).collect(Collectors.joining("\n---\n"));
//                if(!sseSend.sendEventThink(emitter,"任务拆分完成：\n"+taskMessage)) return;
//                //对每个子任务执行，得到结果集
//                List<DistilledResult> results = new ArrayList<>();
//                for(SubTask task:subTasks){
//                    //获得该任务需要使用的工具集
//                    ToolCallback[] tools = getTools(task.toolName());
//                    //初始化执行该任务的智能体
//                    Kanodays88Manus kanodays88Manus = new Kanodays88Manus(tools, openAiChatModel);
//                    if(!sseSend.sendEventThink(emitter,"开始执行任务【"+task.taskName()+"】\n")) return;
//                    //获取该任务对应所需的上游任务的结果
//                    String upStreamTaskResult = checkAndFillUpstreamContext(task, results);
//                    //将上游的结果作为记忆输入给智能体
//                    kanodays88Manus.setMessageList(List.of(upStreamTaskResult).stream().map(s->new SystemMessage(s)).collect(Collectors.toList()));
//
//                    //执行任务，得到本次任务的原始结果
//                    List<String> childResult = kanodays88Manus.run(task.taskContent(),task.taskName(),emitter,sseSend);
//                    //原始结果拼接
//                    String result = childResult.stream().collect(Collectors.joining("/n---/n"));
//                    //蒸馏任务结果
//                    DistilledResult distilledResult = distillSubTaskResult(task, result, decomposedTasks.globalRequiredFields());
//
//                    results.add(distilledResult);
//                }
//
//                //整合结果集和意图，得到最终结果
//                String s = fuseResults(originalTask, results);
//                fileBasedChatMemory.add(conversationId,List.of(new AssistantMessage(s)));//将模型返回的最终结果存入记忆
//                if(!sseSend.sendEventResult(emitter,s)) return;
//                //关闭链接
//                emitter.complete();
//            }catch (Exception e){
//                log.error("PlanExecute 执行失败：{}", e.getMessage());
//                sseSend.sendEventResult(emitter, "执行失败: " + e.getMessage());
//                emitter.completeWithError(e);
//            }finally {
//                //删除ThreadLocal防止内存泄露
//                BaseContent.removeChatId();
//                //释放ThreadLocal
//                RequestContextHolder.resetRequestAttributes();
//                emitter.complete();
//            }
//        });


        //对意图进行任务拆分
        if(!safeSendEventThink(emitter,"开始对任务进行拆分...\n")) return null;
        long t0 = System.currentTimeMillis();
        DecomposedTasks decomposedTasks = decomposeTaskWithContract(originalTask);
        log.info("[Phase] decomposeTask took {} ms", System.currentTimeMillis() - t0);
        List<SubTask> subTasks = decomposedTasks.subTaskList();
        String taskMessage = subTasks.stream().map(s -> {
            return "任务" + s.taskId() + "：" + s.taskName();
        }).collect(Collectors.joining("\n---\n"));
        if(!safeSendEventThink(emitter,"任务拆分完成：\n"+taskMessage)) return null;
        //对每个子任务执行，得到结果集（并行wave执行）
        ConcurrentHashMap<Integer, DistilledResult> resultMap = new ConcurrentHashMap<>();
        List<Set<Integer>> waves = buildExecutionWaves(subTasks);
        Map<Integer, SubTask> taskMap = subTasks.stream()
                .collect(Collectors.toMap(SubTask::taskId, Function.identity()));

        for (Set<Integer> waveTaskIds : waves) {
            ExecutorService executor = Executors.newFixedThreadPool(waveTaskIds.size());
            try {
                List<CompletableFuture<Void>> futures = new ArrayList<>();
                for (int taskId : waveTaskIds) {
                    SubTask task = taskMap.get(taskId);
                    futures.add(CompletableFuture.runAsync(() -> {
                        long taskStart = System.currentTimeMillis();
                        log.info("[Phase] Starting subtask {}: {}", task.taskId(), task.taskName());
                        try {
                            ToolCallback[] tools = getTools(task.toolName());
                            Kanodays88Manus kanodays88Manus = new Kanodays88Manus(tools, openAiChatModel);
                            safeSendEventThink(emitter, "开始执行任务【" + task.taskName() + "】\n");
                            //获取该任务对应所需的上游任务的结果
                            String upStreamTaskResult = checkAndFillUpstreamContext(task, resultMap);
                            //将上游的结果作为记忆输入给智能体
                            kanodays88Manus.setMessageList(List.of(upStreamTaskResult).stream()
                                    .map(s -> new SystemMessage(s)).collect(Collectors.toList()));

                            //执行任务，得到本次任务的原始结果
                            long tRun = System.currentTimeMillis();
                            List<String> childResult = kanodays88Manus.run(task.taskContent(), task.taskName(), emitter);
                            log.info("[Phase] Subtask {} run() took {} ms", task.taskId(), System.currentTimeMillis() - tRun);
                            //原始结果拼接
                            String result = childResult.stream().collect(Collectors.joining("/n---/n"));
                            //蒸馏任务结果
                            long tDistill = System.currentTimeMillis();
                            DistilledResult distilledResult;
                            if (shouldSkipDistill(task, result)) {
                                distilledResult = new DistilledResult(task.taskId(), result, result, task);
                                log.info("[Optimize] Skipped distill for task {} (already structured)", task.taskName());
                            } else {
                                distilledResult = distillSubTaskResult(task, result, decomposedTasks.globalRequiredFields());
                            }
                            log.info("[Phase] Subtask {} distill took {} ms", task.taskId(), System.currentTimeMillis() - tDistill);

                            resultMap.put(task.taskId(), distilledResult);
                        } catch (Exception e) {
                            log.error("[Optimize] Subtask {} failed: {}", task.taskId(), e.getMessage());
                        }
                    }, executor));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } finally {
                executor.shutdown();
            }
        }

        //整合结果集和意图，得到最终结果
        long tFuse = System.currentTimeMillis();
        List<DistilledResult> orderedResults = subTasks.stream()
                .map(t -> resultMap.get(t.taskId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        String s = fuseResults(originalTask, orderedResults, decomposedTasks.globalRequiredFields());
        log.info("[Phase] fuseResults took {} ms", System.currentTimeMillis() - tFuse);
        log.info("[Phase] TOTAL planExecute took {} ms", System.currentTimeMillis() - overallStart);
        return s;
    }




    private boolean safeSendEventThink(SseEmitter emitter, String data) {
        synchronized (sseLock) {
            return SSESend.sendEventThink(emitter, data);
        }
    }

    //从所需工具名称集合中获取到具体工具集合
    public ToolCallback[] getTools(Set<String> toolNames){
        ToolCallback[] toolCallbacks = Arrays.stream(allTools).filter(t -> (toolNames.contains(t.getToolDefinition().name())||t.getToolDefinition().name().equals("assignmentFinish"))).toArray(ToolCallback[]::new);
        return toolCallbacks;
    }
    //任务分解
    public DecomposedTasks decomposeTaskWithContract(String task) {
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
            4. 工具信息只做任务划分参考，不得使用工具

            输出格式要求：{format}
            """;
//        String userPrompt = """
//                    核心目标：{mainGoal}
//                    约束条件：{constraints}
//                    交付要求：{deliverables}
//                """;

        return chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("format", converter.getJsonSchema()))
                .user(task)
                .toolCallbacks(allTools)
                .call()
                .entity(converter);
    }

    /**
     * Build execution waves from dependency graph (DAG topological sort).
     * Wave 1 = tasks with no dependencies, Wave 2 = tasks depending on Wave 1, etc.
     */
    private List<Set<Integer>> buildExecutionWaves(List<SubTask> subTasks) {
        // dependsOn: for each task, which upstream taskIds it depends on
        Map<Integer, Set<Integer>> dependsOn = new HashMap<>();
        Map<Integer, SubTask> taskMap = new HashMap<>();

        for (SubTask task : subTasks) {
            taskMap.put(task.taskId(), task);
            dependsOn.put(task.taskId(), new HashSet<>());
        }

        // For each task, find tasks whose downstreamTaskIds contain this task's ID
        // Those are the tasks this task depends on
        for (SubTask task : subTasks) {
            for (SubTask other : subTasks) {
                if (other.downstreamTaskIds().contains(task.taskId())) {
                    dependsOn.get(task.taskId()).add(other.taskId());
                }
            }
        }

        List<Set<Integer>> waves = new ArrayList<>();
        Set<Integer> remaining = new HashSet<>(taskMap.keySet());

        while (!remaining.isEmpty()) {
            Set<Integer> wave = new HashSet<>();
            for (int taskId : remaining) {
                if (dependsOn.get(taskId).isEmpty()) {
                    wave.add(taskId);
                }
            }

            if (wave.isEmpty()) {
                // Circular dependency fallback: add all remaining as one wave
                wave.addAll(remaining);
            }

            waves.add(wave);
            remaining.removeAll(wave);

            // Remove completed tasks from dependency sets
            for (int taskId : remaining) {
                dependsOn.get(taskId).removeAll(wave);
            }
        }

        return waves;
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
     * Overloaded version that accepts a Map<Integer, DistilledResult> for parallel execution support.
     */
    private String checkAndFillUpstreamContext(SubTask currentTask, Map<Integer, DistilledResult> upstreamResults) {
        StringBuilder context = new StringBuilder();
        if (upstreamResults.isEmpty()) return "";

        for (DistilledResult upstreamResult : upstreamResults.values()) {
            if (!upstreamResult.subTask().downstreamTaskIds().contains(currentTask.taskId())) continue;

            SubTask upstreamTask = upstreamResult.subTask();
            String coreResult = upstreamResult.structuredCoreResult();

            context.append("上游任务:").append(upstreamTask.taskContent()).append("\n核心结果:").append(coreResult).append("\n---\n");
        }
        return context.toString();
    }

    /**
     * Determine whether a subtask result can skip LLM distillation.
     * Returns true when the raw result is already valid JSON containing all required fields
     * and is concise enough (<2000 chars), making further LLM distillation unnecessary.
     */
    private boolean shouldSkipDistill(SubTask task, String rawResult) {
        try {
            JsonNode json = new ObjectMapper().readTree(rawResult);
            Set<String> required = task.requiredFields();
            if (required != null && !required.isEmpty()) {
                for (String field : required) {
                    if (!json.has(field)) return false;
                }
            }
            return rawResult.length() < 2000;
        } catch (Exception e) {
            return false;
        }
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
    private String fuseResults(String task, List<DistilledResult> subTaskResults, Set<String> globalRequiredFields) {
        List<String> results = subTaskResults.stream().map(s -> s.structuredCoreResult()).collect(Collectors.toList());
        String prompt = """
            基于以下子任务的执行结果，整合成最终完整的任务报告返回给用户。
            不要遗漏任何子任务的关键结果。
            【核心目标】：{mainGoal}
            【全局强制留存字段】：{globalFields}
            【子任务结果列表】：
            {subTaskResults}
            """;

        return chatClient.prompt()
                .system(s -> s.text(prompt)
                        .param("mainGoal", task)
                        .param("globalFields", String.join(", ", globalRequiredFields))
                        .param("subTaskResults", String.join("\n---\n", results)))
                .call()
                .content();
    }

}



















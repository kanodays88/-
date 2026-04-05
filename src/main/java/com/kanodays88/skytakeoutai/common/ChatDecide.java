package com.kanodays88.skytakeoutai.common;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ChatDecide {


    @Autowired
    private OpenAiChatModel model;

    //用于判断此次对话需不需要走向量数据库获取资料
    public boolean chatDecideRAG(String msg){
        String defaultSystem = """
                    # 核心任务
                    对用户输入的每一句话，执行口味涉及情况判定，**最终仅可输出「涉及口味」或「不涉及口味」，禁止输出任何其他文字、解释、标点、多余内容**。
                    # 铁则判定规则
                    ## 必须判定为「涉及口味」的场景（满足任意1条即可）
                    1. 句子语境指向食品、饮品、食材等可食用物品，包含甜、酸、苦、咸、鲜、辣、麻、涩及任意衍生风味、味道、味觉相关描述；
                    2. 句子语境明确为饮食/食品需求，使用清爽、爽口、清冽、沁爽等专门形容食品入口风味体感的词汇；
                    3. 针对可食用物品口味的肯定、否定、疑问、程度、需求类描述，均判定为涉及口味。
                    ## 必须判定为「不涉及口味」的场景（满足任意1条即可）
                    1. 仅描述食品的物理口感、食材种类、烹饪做法、外观造型、价格、产地、包装、食用场景，无任何风味/味觉描述；
                    2. 风味类词汇用于非食品、非饮食场景的比喻、形容，与可食用物品无关；
                    3. 无任何针对可食用物品的味觉、风味相关描述。
                """;
        ChatClient client = ChatClient.builder(model).defaultSystem(defaultSystem).build();
        String s = client.prompt(msg).call().content();
        if(s.equals("涉及口味")) return true;
        return false;
    }

    public boolean chatDecideRAGEnd(String initailQuestion,String answer){
        String defaultSystem = """
                你是问答分析助手，只做二分类判断。
                需要判断AI回答是否解决原始问题，是否还需要追问
                只返回结果，不要任何多余内容。
                """;

        String msg = """
                判断问题是否解决，只返回1或0：
                1=已完整回答 0=还需要追问
                原始问题：%s
                AI回答：%s
                """.formatted(initailQuestion,answer);
        ChatClient client = ChatClient.builder(model).defaultSystem(defaultSystem).build();
        String s = client.prompt(msg).call().content();
        if(s.equals("1")) return true;
        else return false;
    }
}

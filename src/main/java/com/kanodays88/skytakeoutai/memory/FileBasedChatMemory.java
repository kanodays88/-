package com.kanodays88.skytakeoutai.memory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class FileBasedChatMemory implements ChatMemory {

    //文件存储路径
    private final String BASE_DIR;

    private final int lastN = 10;//获取的记忆数

    //kryo序列化库
    private static final Kryo kryo = new Kryo();

    static {
        //取消手动实例化策略
        kryo.setRegistrationRequired(false);
        //采用自动序列化策略
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBasedChatMemory(String path){
        BASE_DIR = path;//指定文件创建路径
        File file = new File(path);
        if(!file.exists()){
            //如果文件不存在，先创建文件
            file.mkdir();
        }
    }

    @Override
    public void add(String conversationId, List<Message> messages) {
        //先从文件获取到消息列表
        List<Message> fileMessage = getOrCreateConversation(conversationId);
        //将新的消息列表加入
        fileMessage.addAll(messages);
        //写入文件
        saveConversation(conversationId,fileMessage);
    }


    @Override
    public List<Message> get(String conversationId) {
        List<Message> messages = getOrCreateConversation(conversationId);
        return messages.stream().skip(Math.max(0,messages.size()-lastN)).toList();
    }

    @Override
    public void clear(String conversationId) {
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.delete();
        }
    }
    /**
     * 根据会话id从文件中取出会话信息
     * @param conversationId
     * @return
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                //由于kryo在序列化时将写入信息的类型也一并写入，所以取出反序列化时也会有类型的信息，自然能够识别出List<Message>而不是List<Object>
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return messages;
    }

    /**
     * 将消息保存按照id保存到文件中
     * @param conversationId  会话id
     * @param messages  消息
     */
    private void saveConversation(String conversationId, List<Message> messages) {
        File file = getConversationFile(conversationId);
        //try()这样些可以自动关闭资源流，可以偷懒不写finally
        //获取文件输出流，用Kryo 库提供的二进制处理流包装，提供更便捷、高效的二进制数据写入
        try (Output output = new Output(new FileOutputStream(file))) {
            //递归遍历对象图：分析 messages 列表本身，以及列表里每个 Message 对象的内部字段（如 String content、long timestamp 等）。
            //生成二进制字节：根据对象的【类型信息】和字段值，生成极其紧凑的二进制字节流（比 Java 原生序列化体积小很多）。
            //写入流中：将生成的字节流通过 Output → FileOutputStream 层层传递，最终写入磁盘文件。
            kryo.writeObject(output, messages);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //获取文件路径
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }


}



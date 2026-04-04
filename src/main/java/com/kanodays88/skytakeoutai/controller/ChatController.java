package com.kanodays88.skytakeoutai.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/ai/chat")
public class ChatController {

    @Autowired
    private ChatClient chatClient;

    @RequestMapping(value = "/{msg}", produces = "text/html;charset=utf-8")
    public Flux<String> serviceChat(@PathVariable("msg") String msg){
        return chatClient.prompt(msg).stream().content();
    }
}

package org.example.ollama.model;

import java.util.List;

public class ChatResponse {
    private String reply;
    private List<Object> chatMemory;

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    public List<Object> getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(List<Object> chatMemory) {
        this.chatMemory = chatMemory;
    }
}

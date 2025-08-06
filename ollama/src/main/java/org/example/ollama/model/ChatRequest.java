package org.example.ollama.model;

import java.util.List;

public class ChatRequest {
    private List<ChatMessage> messages;
    private String chatId;
    private List<Object> chatMemory;

    public List<ChatMessage> getMessages() {
        return messages;
    }

    public void setMessages(List<ChatMessage> messages) {
        this.messages = messages;
    }

    public String getChatId() {
        return chatId;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public List<Object> getChatMemory() {
        return chatMemory;
    }

    public void setChatMemory(List<Object> chatMemory) {
        this.chatMemory = chatMemory;
    }
}

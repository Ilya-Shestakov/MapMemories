package com.example.mapmemories;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String postId; // ID поста, который отправляем
    private long timestamp;
    private String type; // "post" (на будущее можно добавить "text")

    public ChatMessage() {}

    public ChatMessage(String senderId, String receiverId, String postId, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.postId = postId;
        this.timestamp = timestamp;
        this.type = "post";
    }

    // Геттеры и сеттеры
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}
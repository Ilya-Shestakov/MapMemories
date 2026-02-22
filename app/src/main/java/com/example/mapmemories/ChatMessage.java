package com.example.mapmemories;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String postId;
    private String text; // Новое поле для текста
    private String type; // "text" или "post"
    private long timestamp;
    private String deletedBy;

    // Пустой конструктор нужен для Firebase
    public ChatMessage() {
    }

    // Конструктор для текстового сообщения
    public ChatMessage(String senderId, String receiverId, String text, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
    }

    // Конструктор для поста
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

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }
}
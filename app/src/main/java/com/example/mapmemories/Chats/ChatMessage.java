package com.example.mapmemories.Chats;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private String postId;
    private String imageUrl;
    private long timestamp;
    private String type; // "text", "post", "image"
    private String deletedBy;

    // ИСПРАВЛЕНИЕ: Поле должно называться "read", чтобы Firebase корректно его парсил
    private boolean read;

    public ChatMessage() {
        this.read = false;
    }

    public ChatMessage(String senderId, String receiverId, String text, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.read = false;
    }

    public ChatMessage(String senderId, String receiverId, String postId, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.postId = postId;
        this.timestamp = timestamp;
        this.type = "post";
        this.read = false;
    }

    public ChatMessage(String senderId, String receiverId, String imageUrl, String text, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.imageUrl = imageUrl;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.read = false;
    }

    // Геттеры и сеттеры
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getSenderId() { return senderId; }
    public String getReceiverId() { return receiverId; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getPostId() { return postId; }
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public long getTimestamp() { return timestamp; }
    public String getType() { return type; }
    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    // Геттер/Сеттер для Firebase
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
}
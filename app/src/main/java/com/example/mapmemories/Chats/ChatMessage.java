package com.example.mapmemories.Chats;

public class ChatMessage {
    private String messageId;
    private String senderId;
    private String receiverId;
    private String text;
    private String imageUrl;
    private String postId;
    private long timestamp;
    private String type; // "text", "image", "post"
    private String deletedBy;
    private boolean read;

    // --- НОВЫЕ ПОЛЯ ДЛЯ ОТВЕТА ---
    private String replyMessageId;
    private String replySenderId;
    private String replyText;

    public ChatMessage() {
        // Пустой конструктор для Firebase
    }

    // Конструктор для текста
    public ChatMessage(String senderId, String receiverId, String text, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.read = false;
    }

    // Конструктор для картинки
    public ChatMessage(String senderId, String receiverId, String imageUrl, String text, long timestamp, String type) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.imageUrl = imageUrl;
        this.text = text;
        this.timestamp = timestamp;
        this.type = type;
        this.read = false;
    }

    // Конструктор для поста
    public ChatMessage(String senderId, String receiverId, String postId, long timestamp) {
        this.senderId = senderId;
        this.receiverId = receiverId;
        this.postId = postId;
        this.timestamp = timestamp;
        this.type = "post";
        this.read = false;
    }

    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }

    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }

    public String getReceiverId() { return receiverId; }
    public void setReceiverId(String receiverId) { this.receiverId = receiverId; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPostId() { return postId; }
    public void setPostId(String postId) { this.postId = postId; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDeletedBy() { return deletedBy; }
    public void setDeletedBy(String deletedBy) { this.deletedBy = deletedBy; }

    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }

    // --- ГЕТТЕРЫ И СЕТТЕРЫ ДЛЯ ОТВЕТА ---
    public String getReplyMessageId() { return replyMessageId; }
    public void setReplyMessageId(String replyMessageId) { this.replyMessageId = replyMessageId; }

    public String getReplySenderId() { return replySenderId; }
    public void setReplySenderId(String replySenderId) { this.replySenderId = replySenderId; }

    public String getReplyText() { return replyText; }
    public void setReplyText(String replyText) { this.replyText = replyText; }
}
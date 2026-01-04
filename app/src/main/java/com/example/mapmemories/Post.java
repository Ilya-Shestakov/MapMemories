package com.example.mapmemories;

import com.google.firebase.database.PropertyName;

public class Post {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String mediaUrl;
    private String mediaType; // "image" или "video"
    private double latitude;
    private double longitude;

    // Поле в коде isPublic, но в базе оно называется "public"
    private boolean isPublic;

    private long timestamp;

    public Post() {
        // Пустой конструктор обязателен для Firebase
    }

    public Post(String id, String userId, String title, String description, String mediaUrl, String mediaType, double latitude, double longitude, boolean isPublic, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.mediaUrl = mediaUrl;
        this.mediaType = mediaType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isPublic = isPublic;
        this.timestamp = timestamp;
    }

    // --- Getters & Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getMediaUrl() { return mediaUrl; }
    public void setMediaUrl(String mediaUrl) { this.mediaUrl = mediaUrl; }

    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }

    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }

    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    // ВАЖНО: Аннотация указывает Firebase, что в JSON это поле называется "public"
    @PropertyName("public")
    public boolean isPublic() {
        return isPublic;
    }

    @PropertyName("public")
    public void setPublic(boolean isPublic) {
        this.isPublic = isPublic;
    }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}
package com.example.mapmemories;

public class Post {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String mediaUrl;
    private String mediaType; // "image" или "video"
    private double latitude;
    private double longitude;
    private boolean isPublic;
    private long timestamp;

    public Post() {
        // Пустой конструктор нужен для Firebase
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

    // Геттеры и сеттеры (обязательно для Firebase)
    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public String getMediaUrl() { return mediaUrl; }
    public String getMediaType() { return mediaType; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public boolean isPublic() { return isPublic; }
    public long getTimestamp() { return timestamp; }
}
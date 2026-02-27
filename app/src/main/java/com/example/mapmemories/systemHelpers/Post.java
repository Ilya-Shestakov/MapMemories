package com.example.mapmemories.systemHelpers;

import com.google.firebase.database.PropertyName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Post {
    private String id;
    private String userId;
    private String title;
    private String description;
    private String mediaUrl; // Оставляем для совместимости со старыми постами
    private List<String> mediaUrls; // НОВОЕ: Список ссылок для карусели
    private String mediaType;
    private double latitude;
    private double longitude;

    @PropertyName("public")
    public boolean isPublic;

    private long timestamp;
    private Map<String, Boolean> likes = new HashMap<>();

    public Post() {}

    public Post(String id, String userId, String title, String description, String mediaUrl, List<String> mediaUrls, String mediaType, double latitude, double longitude, boolean isPublic, long timestamp) {
        this.id = id;
        this.userId = userId;
        this.title = title;
        this.description = description;
        this.mediaUrl = mediaUrl;
        this.mediaUrls = mediaUrls;
        this.mediaType = mediaType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isPublic = isPublic;
        this.timestamp = timestamp;
    }

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
    public List<String> getMediaUrls() { return mediaUrls; }
    public void setMediaUrls(List<String> mediaUrls) { this.mediaUrls = mediaUrls; }
    public String getMediaType() { return mediaType; }
    public void setMediaType(String mediaType) { this.mediaType = mediaType; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }

    @PropertyName("public")
    public boolean isPublic() { return isPublic; }
    @PropertyName("public")
    public void setPublic(boolean isPublic) { this.isPublic = isPublic; }

    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    public Map<String, Boolean> getLikes() { return likes; }
    public void setLikes(Map<String, Boolean> likes) { this.likes = likes; }

    public int getLikeCount() {
        return likes == null ? 0 : likes.size();
    }
}
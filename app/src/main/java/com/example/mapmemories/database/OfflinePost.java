package com.example.mapmemories.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "offline_posts")
public class OfflinePost {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String title;
    public String description;
    public String mediaUriString; // Путь к файлу на телефоне
    public String mediaType;      // image или video
    public double latitude;
    public double longitude;
    public boolean isPublic;
    public long timestamp;

    public OfflinePost(String title, String description, String mediaUriString, String mediaType, double latitude, double longitude, boolean isPublic, long timestamp) {
        this.title = title;
        this.description = description;
        this.mediaUriString = mediaUriString;
        this.mediaType = mediaType;
        this.latitude = latitude;
        this.longitude = longitude;
        this.isPublic = isPublic;
        this.timestamp = timestamp;
    }
}
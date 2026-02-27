package com.example.mapmemories.Lenta;

import org.osmdroid.util.BoundingBox;

// Простой класс для описания региона
public class MapRegion {
    String id; // Уникальный ID (например, "spb_lo")
    String name; // Название для юзера
    BoundingBox bbox; // Границы региона
    boolean isDownloaded; // Статус (надо хранить в SharedPreferences)

    public MapRegion(String id, String name, BoundingBox bbox, boolean isDownloaded) {
        this.id = id;
        this.name = name;
        this.bbox = bbox;
        this.isDownloaded = isDownloaded;
    }
}
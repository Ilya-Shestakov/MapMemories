package com.example.mapmemories;

import android.app.Application;
import android.content.SharedPreferences;
import androidx.appcompat.app.AppCompatDelegate;

public class MapMemoriesApp extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Загружаем сохраненную тему при старте приложения
        SharedPreferences prefs = getSharedPreferences("AppPrefs", MODE_PRIVATE);
        // По умолчанию делаем темную тему (true)
        boolean isDarkTheme = prefs.getBoolean("dark_theme_enabled", true);

        if (isDarkTheme) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
        }
    }
}
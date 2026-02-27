package com.example.mapmemories.systemHelpers;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.widget.Toast;

public class DownloadHelper {

    public static void downloadImage(Context context, String url) {
        if (url == null || url.isEmpty()) {
            Toast.makeText(context, "Ссылка на фото пуста", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
            request.setTitle("MapMemories Photo");
            request.setDescription("Скачивание фотографии...");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "MapMemories_" + System.currentTimeMillis() + ".jpg");

            DownloadManager manager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            if (manager != null) {
                manager.enqueue(request);
                Toast.makeText(context, "Скачивание началось...", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, "Ошибка скачивания: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
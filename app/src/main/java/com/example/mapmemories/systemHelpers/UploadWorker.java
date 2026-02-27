package com.example.mapmemories.systemHelpers;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.cloudinary.Cloudinary;
import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.database.OfflinePost;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class UploadWorker extends Worker {

    private final AppDatabase db;
    private final Cloudinary cloudinary;

    public UploadWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        db = AppDatabase.getDatabase(context);

        // Инициализация Cloudinary
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    @NonNull
    @Override
    public Result doWork() {
        List<OfflinePost> posts = db.offlinePostDao().getAllPostsSync();
        if (posts.isEmpty()) return Result.success();

        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");
        String userId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "anon";

        for (OfflinePost post : posts) {
            try {
                // 1. Загрузка файла в Cloudinary
                File mediaFile = new File(post.mediaUriString);
                if (!mediaFile.exists()) {
                    // Если файл удален с телефона, удаляем запись из БД и пропускаем
                    db.offlinePostDao().delete(post);
                    continue;
                }

                Map<String, Object> params = new HashMap<>();
                params.put("resource_type", "auto");
                Map uploadResult = cloudinary.uploader().upload(mediaFile, params);
                String uploadedUrl = (String) uploadResult.get("secure_url");

                // СОЗДАЕМ СПИСОК ИЗ ОДНОЙ ССЫЛКИ ДЛЯ НОВОГО КОНСТРУКТОРА
                List<String> mediaUrls = Collections.singletonList(uploadedUrl);

                // 2. Сохранение в Firebase (теперь передаем mediaUrls)
                String postId = postsRef.push().getKey();
                Post firebasePost = new Post(
                        postId, userId, post.title, post.description,
                        uploadedUrl, mediaUrls, post.mediaType, post.latitude, post.longitude,
                        post.isPublic, post.timestamp
                );

                if (postId != null) {
                    postsRef.child(postId).setValue(firebasePost);
                }

                // 3. Удаляем из локальной БД после успеха
                db.offlinePostDao().delete(post);

            } catch (Exception e) {
                e.printStackTrace();
                return Result.retry(); // Если ошибка сети, попробуем позже
            }
        }

        return Result.success();
    }
}
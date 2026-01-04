package com.example.mapmemories;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class CreatePostActivity extends AppCompatActivity {

    // UI
    private ImageButton btnClose;
    private CardView btnAddMedia;
    private ImageView previewImage;
    private LinearLayout layoutAddMediaPlaceholder; // Внутренний layout с иконкой камеры
    private TextInputEditText inputTitle, inputDescription;
    private LinearLayout btnSelectLocation;
    private TextView textLocation;
    private MaterialButton btnSavePost;
    private Switch switchBar;

    // Data
    private Uri selectedMediaUri;
    private String selectedMediaType = "image"; // "image" или "video"
    private double selectedLat = 0.0;
    private double selectedLng = 0.0;

    // Services
    private Cloudinary cloudinary;
    private DatabaseReference postsRef;
    private FirebaseAuth mAuth;
    private ProgressDialog progressDialog;

    // Launchers
    private ActivityResultLauncher<Intent> mediaPickerLauncher;
    private ActivityResultLauncher<Intent> locationPickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        initFirebase();
        initCloudinary();
        initViews();
        setupLaunchers();
        setupListeners();
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        // Ссылка на узел "posts" в базе данных
        postsRef = FirebaseDatabase.getInstance().getReference("posts");
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp"); // Твои данные из Profile
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    private void initViews() {
        btnClose = findViewById(R.id.btnClose);
        btnAddMedia = findViewById(R.id.btnAddMedia);
        previewImage = findViewById(R.id.previewImage);
        // Находим LinearLayout внутри CardView, чтобы скрыть его при выборе фото
        layoutAddMediaPlaceholder = (LinearLayout) btnAddMedia.getChildAt(0);

        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);
        textLocation = findViewById(R.id.textLocation);
        btnSavePost = findViewById(R.id.btnSavePost);
        switchBar = findViewById(R.id.switchBar);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Публикация воспоминания...");
        progressDialog.setCancelable(false);
    }

    private void setupLaunchers() {
        // Лаунчер для выбора медиа (фото или видео)
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedMediaUri = result.getData().getData();

                        // Определяем тип (простая проверка, можно улучшить через ContentResolver)
                        String mimeType = getContentResolver().getType(selectedMediaUri);
                        if (mimeType != null && mimeType.startsWith("video")) {
                            selectedMediaType = "video";
                        } else {
                            selectedMediaType = "image";
                        }

                        showPreview();
                    }
                }
        );

        // Лаунчер для карты (предполагаем, что MapActivity вернет координаты)
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // Предполагаем, что ты вернешь эти ключи из MapActivity
                        selectedLat = result.getData().getDoubleExtra("lat", 0.0);
                        selectedLng = result.getData().getDoubleExtra("lng", 0.0);
                        String addressName = result.getData().getStringExtra("address");

                        textLocation.setText(addressName != null ? addressName : selectedLat + ", " + selectedLng);
                    }
                }
        );
    }

    private void showPreview() {
        layoutAddMediaPlaceholder.setVisibility(View.GONE);
        previewImage.setVisibility(View.VISIBLE);

        Glide.with(this)
                .load(selectedMediaUri)
                .centerCrop()
                .into(previewImage);
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            finish();
        });

        btnAddMedia.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            // Открываем галерею для выбора фото и видео
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*"); // Разрешаем все, но фильтруем ниже
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
            mediaPickerLauncher.launch(intent);
        });

        btnSelectLocation.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            // TODO: Замени MapActivity.class на свой класс карты
            // Intent intent = new Intent(this, MapActivity.class);
            // intent.putExtra("isPicker", true); // Флаг, что мы выбираем точку
            // locationPickerLauncher.launch(intent);

            Toast.makeText(this, "Тут должен быть переход на карту", Toast.LENGTH_SHORT).show();
            // Временно имитируем выбор координат для теста
            selectedLat = 55.7558;
            selectedLng = 37.6173;
            textLocation.setText("Москва (Тест)");
        });

        btnSavePost.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            validateAndUpload();
        });
    }

    private void validateAndUpload() {
        String title = inputTitle.getText().toString().trim();
        String desc = inputDescription.getText().toString().trim();

        if (title.isEmpty()) {
            inputTitle.setError("Введите название");
            return;
        }
        if (selectedMediaUri == null) {
            Toast.makeText(this, "Пожалуйста, выберите фото или видео", Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog.show();
        uploadToCloudinary(title, desc);
    }

    private void uploadToCloudinary(String title, String desc) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream;

                // Если это изображение, сжимаем его
                if (selectedMediaType.equals("image")) {
                    inputStream = getCompressedImageStream(selectedMediaUri);
                } else {
                    // Видео грузим как есть (сжатие видео на клиенте требует тяжелых библиотек типа FFmpeg)
                    inputStream = getContentResolver().openInputStream(selectedMediaUri);
                }

                // Параметры загрузки Cloudinary
                Map<String, Object> uploadParams = new HashMap<>();
                uploadParams.put("resource_type", "auto"); // Автоматически определить фото или видео

                // Загрузка
                Map uploadResult = cloudinary.uploader().upload(inputStream, uploadParams);
                String uploadedUrl = (String) uploadResult.get("secure_url");

                // Сохранение в Firebase
                savePostToFirebase(title, desc, uploadedUrl);

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(CreatePostActivity.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    // Метод для сжатия изображения перед отправкой
    private InputStream getCompressedImageStream(Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri));
        } else {
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        // Сжимаем в JPEG с качеством 60%
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos);
        byte[] bitmapData = bos.toByteArray();

        return new ByteArrayInputStream(bitmapData);
    }

    private void savePostToFirebase(String title, String desc, String mediaUrl) {
        String postId = postsRef.push().getKey(); // Генерируем уникальный ID
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "anon";
        boolean isPublic = switchBar.isChecked();

        Post newPost = new Post(
                postId,
                userId,
                title,
                desc,
                mediaUrl,
                selectedMediaType,
                selectedLat,
                selectedLng,
                isPublic,
                System.currentTimeMillis()
        );

        if (postId != null) {
            postsRef.child(postId).setValue(newPost)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Воспоминание сохранено!", Toast.LENGTH_SHORT).show();
                        finish(); // Закрываем активити
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Ошибка сохранения в БД: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
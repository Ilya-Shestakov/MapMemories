package com.example.mapmemories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.database.OfflinePost;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class CreatePostActivity extends AppCompatActivity {

    // --- UI Элементы ---
    private ConstraintLayout rootLayout;
    private ImageButton btnClose;
    private CardView btnAddMedia;
    private ImageView previewImage;
    private LinearLayout layoutAddMediaPlaceholder;
    private TextInputEditText inputTitle, inputDescription;
    private LinearLayout btnSelectLocation;
    private TextView textLocation;
    private MaterialButton btnSavePost;

    private SwipeBackHelper swipeBackHelper;

    // Элементы переключателя приватности (ИЗМЕНЕНО: теперь LinearLayout)
    private LinearLayout btnPrivacyToggle;
    private ImageView ivPrivacyIcon, ivPrivacyStateIndicator;
    private TextView tvPrivacyText;

    // --- Данные ---
    private Uri selectedMediaUri;
    private String selectedMediaType = "image";
    private double selectedLat = 0.0;
    private double selectedLng = 0.0;
    private boolean isPublic = false;

    // --- Сервисы ---
    private Cloudinary cloudinary;
    private DatabaseReference postsRef;
    private FirebaseAuth mAuth;
    private ProgressDialog progressDialog;

    // --- Лаунчеры ---
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

        // UI приватности
        updatePrivacyUI(false);

        swipeBackHelper = new SwipeBackHelper(this);

        // --- ЛОГИКА АНИМАЦИИ ОТКРЫТИЯ (Circular Reveal) ---
        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            final View rootLayout = findViewById(android.R.id.content);
            rootLayout.setVisibility(View.INVISIBLE);

            android.view.ViewTreeObserver viewTreeObserver = rootLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                        int revealX = getIntent().getIntExtra("revealX", 0);
                        int revealY = getIntent().getIntExtra("revealY", 0);

                        float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);

                        android.animation.Animator circularReveal = android.view.ViewAnimationUtils.createCircularReveal(rootLayout, revealX, revealY, 0, finalRadius);
                        circularReveal.setDuration(400);
                        circularReveal.setInterpolator(new android.view.animation.AccelerateInterpolator());

                        rootLayout.setVisibility(View.VISIBLE);
                        circularReveal.start();
                    }
                });
            }
        } else {
            // Если анимации нет, просто показываем контент (на всякий случай)
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
        }
    }

    private void revealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);

        Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootLayout, x, y, 0, finalRadius);
        circularReveal.setDuration(400);
        circularReveal.setInterpolator(new AccelerateInterpolator());

        rootLayout.setVisibility(View.VISIBLE);
        circularReveal.start();
    }

    private void unRevealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootLayout, x, y, finalRadius, 0);

        circularReveal.setDuration(500);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                rootLayout.setVisibility(View.INVISIBLE);
                finish();
                overridePendingTransition(0, 0);
            }
        });
        circularReveal.start();
    }

    private void initFirebase() {
        mAuth = FirebaseAuth.getInstance();
        postsRef = FirebaseDatabase.getInstance().getReference("posts");
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    private void initViews() {
        // Находим корневой элемент
        rootLayout = findViewById(R.id.rootLayout);

        btnClose = findViewById(R.id.btnClose);
        btnAddMedia = findViewById(R.id.btnAddMedia);
        previewImage = findViewById(R.id.previewImage);

        // ИСПРАВЛЕНИЕ: Ищем по ID, а не через getChildAt, чтобы избежать ClassCastException
        layoutAddMediaPlaceholder = findViewById(R.id.layoutAddMediaPlaceholder);

        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);
        textLocation = findViewById(R.id.textLocation);
        btnSavePost = findViewById(R.id.btnSavePost);

        // ИСПРАВЛЕНИЕ: btnPrivacyToggle в XML теперь LinearLayout
        btnPrivacyToggle = findViewById(R.id.btnPrivacyToggle);
        ivPrivacyIcon = findViewById(R.id.ivPrivacyIcon);
        tvPrivacyText = findViewById(R.id.tvPrivacyText);
        ivPrivacyStateIndicator = findViewById(R.id.ivPrivacyStateIndicator);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Публикация воспоминания...");
        progressDialog.setCancelable(false);
    }

    private void setupLaunchers() {
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedMediaUri = result.getData().getData();
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

        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        selectedLat = result.getData().getDoubleExtra("lat", 0.0);
                        selectedLng = result.getData().getDoubleExtra("lng", 0.0);
                        String addressName = result.getData().getStringExtra("address");
                        textLocation.setText(addressName != null ? addressName : selectedLat + ", " + selectedLng);
                    }
                }
        );
    }

    private void showPreview() {
        if (layoutAddMediaPlaceholder != null) {
            layoutAddMediaPlaceholder.setVisibility(View.GONE);
        }
        previewImage.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(selectedMediaUri)
                .centerCrop()
                .into(previewImage);
    }

    private void setupListeners() {

        btnClose.setOnClickListener(v -> onBackPressed());

        btnAddMedia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
            mediaPickerLauncher.launch(intent);
        });

        btnSelectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(CreatePostActivity.this, PickLocationActivity.class);
            locationPickerLauncher.launch(intent);
        });

        btnPrivacyToggle.setOnClickListener(v -> togglePrivacy());
        btnSavePost.setOnClickListener(v -> validateAndUpload());
    }

    private void togglePrivacy() {
        isPublic = !isPublic;
        int colorPrivate = ContextCompat.getColor(this, android.R.color.transparent); // Или R.color.secondary если нужен цвет
        int colorPublic = ContextCompat.getColor(this, R.color.online_indicator);
        // В дизайне из XML фон кнопки прозрачный/selectableItemBackground.
        // Если хотите менять цвет фона всей строки:
        // Используем прозрачный для "приватно" (как дефолт) и подкрашенный для "публично".
        // Но лучше просто менять иконку и текст, как в новом дизайне.

        // Для упрощения под новый дизайн - меняем только иконки и текст.
        // Если хотите менять цвет фона LinearLayout:
        /*
        int startColor = isPublic ? colorPrivate : colorPublic;
        int endColor = isPublic ? colorPublic : colorPrivate;
        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(animator ->
                btnPrivacyToggle.setBackgroundColor((int) animator.getAnimatedValue()) // ИСПРАВЛЕНО на setBackgroundColor
        );
        colorAnimation.start();
        */

        ivPrivacyIcon.animate()
                .rotationY(90f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (isPublic) {
                        ivPrivacyIcon.setImageResource(R.drawable.ic_public);
                        ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.online_indicator));
                        tvPrivacyText.setText("Публично (видят все)");
                    } else {
                        ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
                        ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
                        tvPrivacyText.setText("Приватно (только я)");
                    }
                    ivPrivacyIcon.setRotationY(-90f);
                    ivPrivacyIcon.animate().rotationY(0f).setDuration(150).start();
                })
                .start();
        ivPrivacyStateIndicator.animate().rotationBy(180f).setDuration(300).start();
    }

    private void updatePrivacyUI(boolean animate) {
        if (isPublic) {
            ivPrivacyIcon.setImageResource(R.drawable.ic_public);
            tvPrivacyText.setText("Публично (видят все)");
            ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.online_indicator));
        } else {
            ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
            tvPrivacyText.setText("Приватно (только я)");
            ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
        }
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

        if (NetworkUtils.isConnected(this)) {
            progressDialog.show();
            uploadToCloudinary(title, desc);
        } else {
            savePostOffline(title, desc);
        }
    }

    private void savePostOffline(String title, String desc) {
        new Thread(() -> {
            try {
                File localFile = copyUriToInternalStorage(selectedMediaUri);
                OfflinePost offlinePost = new OfflinePost(
                        title, desc, localFile.getAbsolutePath(), selectedMediaType,
                        selectedLat, selectedLng, isPublic, System.currentTimeMillis()
                );
                AppDatabase.getDatabase(this).offlinePostDao().insert(offlinePost);
                scheduleUploadWorker();

                runOnUiThread(() -> {
                    Toast.makeText(this, "Нет интернета. Сохранено в черновики!", Toast.LENGTH_LONG).show();
                    finish();
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    private void scheduleUploadWorker() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(UploadWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this).enqueueUniqueWork(
                "UPLOAD_POSTS_WORK",
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                uploadWork
        );
    }

    private File copyUriToInternalStorage(Uri uri) throws IOException {
        InputStream inputStream = getContentResolver().openInputStream(uri);
        File destinationFile = new File(getFilesDir(), "offline_media_" + System.currentTimeMillis());

        try (FileOutputStream outputStream = new FileOutputStream(destinationFile)) {
            byte[] buffer = new byte[1024];
            int length;
            while ((inputStream != null) && ((length = inputStream.read(buffer)) > 0)) {
                outputStream.write(buffer, 0, length);
            }
        }
        if (inputStream != null) inputStream.close();
        return destinationFile;
    }

    private void uploadToCloudinary(String title, String desc) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream;
                if (selectedMediaType.equals("image")) {
                    inputStream = getCompressedImageStream(selectedMediaUri);
                } else {
                    inputStream = getContentResolver().openInputStream(selectedMediaUri);
                }
                Map<String, Object> uploadParams = new HashMap<>();
                uploadParams.put("resource_type", "auto");

                Map uploadResult = cloudinary.uploader().upload(inputStream, uploadParams);
                String uploadedUrl = (String) uploadResult.get("secure_url");
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

    private InputStream getCompressedImageStream(Uri uri) throws IOException {
        Bitmap bitmap;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(getContentResolver(), uri));
        } else {
            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, bos);
        return new ByteArrayInputStream(bos.toByteArray());
    }

    private void savePostToFirebase(String title, String desc, String mediaUrl) {
        String postId = postsRef.push().getKey();
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "anon";

        Post newPost = new Post(
                postId, userId, title, desc, mediaUrl,
                selectedMediaType, selectedLat, selectedLng,
                isPublic, System.currentTimeMillis()
        );

        if (postId != null) {
            postsRef.child(postId).setValue(newPost)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Воспоминание сохранено!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    @Override
    public void onBackPressed() {
        if (getIntent().hasExtra("revealX")) {
            int revealX = getIntent().getIntExtra("revealX", 0);
            int revealY = getIntent().getIntExtra("revealY", 0);
            unRevealActivity(revealX, revealY);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) {
            return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        }
        return super.dispatchTouchEvent(ev);
    }

}
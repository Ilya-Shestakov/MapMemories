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
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;

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
import java.util.concurrent.Executors;

public class CreatePostActivity extends AppCompatActivity {

    // --- UI Элементы ---
    private ConstraintLayout rootLayout; // Главный контейнер для анимации
    private ImageButton btnClose;
    private CardView btnAddMedia;
    private ImageView previewImage;
    private LinearLayout layoutAddMediaPlaceholder;
    private TextInputEditText inputTitle, inputDescription;
    private LinearLayout btnSelectLocation;
    private TextView textLocation;
    private MaterialButton btnSavePost;

    // Элементы переключателя приватности
    private CardView btnPrivacyToggle;
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

        // --- ЛОГИКА АНИМАЦИИ ОТКРЫТИЯ (Circular Reveal) ---
        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            rootLayout.setVisibility(View.INVISIBLE);

            ViewTreeObserver viewTreeObserver = rootLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        rootLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealActivity(getIntent().getIntExtra("revealX", 0),
                                getIntent().getIntExtra("revealY", 0));
                    }
                });
            }
        }
    }

    // Метод запуска анимации раскрытия
    private void revealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);

        // Создаем аниматор для этой View
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootLayout, x, y, 0, finalRadius);
        circularReveal.setDuration(400);
        circularReveal.setInterpolator(new AccelerateInterpolator());

        // Делаем видимым и запускаем
        rootLayout.setVisibility(View.VISIBLE);
        circularReveal.start();
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

    private void unRevealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootLayout, x, y, finalRadius, 0);

        circularReveal.setDuration(300);
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
        // Находим корневой элемент (не забудь добавить ID в XML!)
        rootLayout = findViewById(R.id.rootLayout);

        btnClose = findViewById(R.id.btnClose);
        btnAddMedia = findViewById(R.id.btnAddMedia);
        previewImage = findViewById(R.id.previewImage);
        layoutAddMediaPlaceholder = (LinearLayout) btnAddMedia.getChildAt(0);

        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);
        textLocation = findViewById(R.id.textLocation);
        btnSavePost = findViewById(R.id.btnSavePost);

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
        layoutAddMediaPlaceholder.setVisibility(View.GONE);
        previewImage.setVisibility(View.VISIBLE);
        Glide.with(this)
                .load(selectedMediaUri)
                .centerCrop()
                .into(previewImage);
    }

    private void setupListeners() {
        // Изменили finish() на onBackPressed(), чтобы срабатывала обратная анимация
        btnClose.setOnClickListener(v -> onBackPressed());

        btnAddMedia.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[] {"image/*", "video/*"});
            mediaPickerLauncher.launch(intent);
        });

        btnSelectLocation.setOnClickListener(v -> {
            // Заглушка
            Toast.makeText(this, "Выбор локации (заглушка)", Toast.LENGTH_SHORT).show();
            selectedLat = 55.7558;
            selectedLng = 37.6173;
            textLocation.setText("Москва, Кремль");
        });

        btnPrivacyToggle.setOnClickListener(v -> togglePrivacy());
        btnSavePost.setOnClickListener(v -> validateAndUpload());
    }

    private void togglePrivacy() {
        isPublic = !isPublic;
        int colorPrivate = ContextCompat.getColor(this, R.color.surface_dark);
        int colorPublic = ContextCompat.getColor(this, R.color.online_indicator);
        int colorTextPrivate = ContextCompat.getColor(this, R.color.text_secondary);

        int startColor = isPublic ? colorPrivate : colorPublic;
        int endColor = isPublic ? colorPublic : colorPrivate;

        ValueAnimator colorAnimation = ValueAnimator.ofObject(new ArgbEvaluator(), startColor, endColor);
        colorAnimation.setDuration(300);
        colorAnimation.addUpdateListener(animator ->
                btnPrivacyToggle.setCardBackgroundColor((int) animator.getAnimatedValue())
        );
        colorAnimation.start();

        ivPrivacyIcon.animate()
                .rotationY(90f)
                .setDuration(150)
                .withEndAction(() -> {
                    if (isPublic) {
                        ivPrivacyIcon.setImageResource(R.drawable.ic_public);
                        ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.white));
                        tvPrivacyText.setTextColor(ContextCompat.getColor(this, R.color.white));
                        ivPrivacyStateIndicator.setColorFilter(ContextCompat.getColor(this, R.color.white));
                    } else {
                        ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
                        ivPrivacyIcon.setColorFilter(colorTextPrivate);
                        tvPrivacyText.setTextColor(colorTextPrivate);
                        ivPrivacyStateIndicator.setColorFilter(colorTextPrivate);
                    }
                    tvPrivacyText.setText(isPublic ? "Публично (видят все)" : "Приватно (только я)");
                    ivPrivacyIcon.setRotationY(-90f);
                    ivPrivacyIcon.animate().rotationY(0f).setDuration(150).start();
                })
                .start();
        ivPrivacyStateIndicator.animate().rotationBy(180f).setDuration(300).start();
    }

    private void updatePrivacyUI(boolean animate) {
        if (isPublic) {
            btnPrivacyToggle.setCardBackgroundColor(ContextCompat.getColor(this, R.color.online_indicator));
            ivPrivacyIcon.setImageResource(R.drawable.ic_public);
            tvPrivacyText.setText("Публично (видят все)");
            int white = ContextCompat.getColor(this, R.color.white);
            ivPrivacyIcon.setColorFilter(white);
            tvPrivacyText.setTextColor(white);
            ivPrivacyStateIndicator.setColorFilter(white);
        } else {
            btnPrivacyToggle.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_dark));
            ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
            tvPrivacyText.setText("Приватно (только я)");
            int secondary = ContextCompat.getColor(this, R.color.text_secondary);
            ivPrivacyIcon.setColorFilter(secondary);
            tvPrivacyText.setTextColor(secondary);
            ivPrivacyStateIndicator.setColorFilter(secondary);
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
        progressDialog.show();
        uploadToCloudinary(title, desc);
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
                        // Здесь тоже лучше использовать onBackPressed() если хочешь анимацию сворачивания,
                        // но finish() быстрее для пользователя после успеха. Оставлю finish().
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }
}
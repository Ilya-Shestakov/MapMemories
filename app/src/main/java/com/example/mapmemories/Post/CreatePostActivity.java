package com.example.mapmemories.Post;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
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
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
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
import androidx.viewpager2.widget.ViewPager2;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.cloudinary.Cloudinary;
import com.example.mapmemories.Lenta.PickLocationActivity;
import com.example.mapmemories.systemHelpers.ImageCarouselAdapter;
import com.example.mapmemories.systemHelpers.NetworkUtils;
import com.example.mapmemories.systemHelpers.Post;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.SwipeBackHelper;
import com.example.mapmemories.systemHelpers.UploadWorker;
import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.database.OfflinePost;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class CreatePostActivity extends AppCompatActivity {

    private ConstraintLayout rootLayout;
    private ImageButton btnClose;
    private MaterialButton btnSavePostTop;

    private CardView btnAddMedia;
    private LinearLayout layoutAddMediaPlaceholder;
    private ViewPager2 viewPagerMedia;
    private TabLayout tabLayoutDots;
    private LinearLayout layoutMediaControls;
    private MaterialButton btnAddMoreMedia, btnClearMedia;

    private EditText inputTitle, inputDescription;
    private LinearLayout btnSelectLocation;
    private TextView textLocation, tvPrivacyText;
    private ImageView ivPrivacyIcon;
    private SwitchMaterial switchPrivacy;

    private SwipeBackHelper swipeBackHelper;

    // СПИСОК ВЫБРАННЫХ ФОТО
    private List<Uri> selectedMediaUris = new ArrayList<>();
    private String selectedMediaType = "image";
    private double selectedLat = 0.0;
    private double selectedLng = 0.0;

    private Cloudinary cloudinary;
    private DatabaseReference postsRef;
    private FirebaseAuth mAuth;
    private ProgressDialog progressDialog;

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

        swipeBackHelper = new SwipeBackHelper(this);

        // Анимация появления
        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            final View root = findViewById(android.R.id.content);
            root.setVisibility(View.INVISIBLE);
            root.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener() {
                @Override
                public void onGlobalLayout() {
                    root.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                    int revealX = getIntent().getIntExtra("revealX", 0);
                    int revealY = getIntent().getIntExtra("revealY", 0);
                    float finalRadius = (float) (Math.max(root.getWidth(), root.getHeight()) * 1.1);
                    Animator circularReveal = ViewAnimationUtils.createCircularReveal(root, revealX, revealY, 0, finalRadius);
                    circularReveal.setDuration(400);
                    circularReveal.setInterpolator(new AccelerateInterpolator());
                    root.setVisibility(View.VISIBLE);
                    circularReveal.start();
                }
            });
        } else {
            findViewById(android.R.id.content).setVisibility(View.VISIBLE);
        }
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
        rootLayout = findViewById(R.id.rootLayout);
        btnClose = findViewById(R.id.btnClose);
        btnSavePostTop = findViewById(R.id.btnSavePostTop);

        btnAddMedia = findViewById(R.id.btnAddMedia);
        layoutAddMediaPlaceholder = findViewById(R.id.layoutAddMediaPlaceholder);
        viewPagerMedia = findViewById(R.id.viewPagerMedia);
        tabLayoutDots = findViewById(R.id.tabLayoutDots);
        layoutMediaControls = findViewById(R.id.layoutMediaControls);
        btnAddMoreMedia = findViewById(R.id.btnAddMoreMedia);
        btnClearMedia = findViewById(R.id.btnClearMedia);

        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);
        textLocation = findViewById(R.id.textLocation);

        ivPrivacyIcon = findViewById(R.id.ivPrivacyIcon);
        tvPrivacyText = findViewById(R.id.tvPrivacyText);
        switchPrivacy = findViewById(R.id.switchPrivacy);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Публикация...");
        progressDialog.setCancelable(false);
    }

    private void setupLaunchers() {
        mediaPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        // ВАЖНО: Мы НЕ очищаем список (selectedMediaUris.clear()), а ДОБАВЛЯЕМ к нему
                        if (result.getData().getClipData() != null) {
                            int count = result.getData().getClipData().getItemCount();
                            for (int i = 0; i < count; i++) {
                                selectedMediaUris.add(result.getData().getClipData().getItemAt(i).getUri());
                            }
                        } else if (result.getData().getData() != null) {
                            selectedMediaUris.add(result.getData().getData());
                        }
                        selectedMediaType = "image";
                        updateMediaUI();
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

    private void updateMediaUI() {
        if (selectedMediaUris.isEmpty()) {
            // Показываем плейсхолдер
            layoutAddMediaPlaceholder.setVisibility(View.VISIBLE);
            viewPagerMedia.setVisibility(View.GONE);
            layoutMediaControls.setVisibility(View.GONE);
            tabLayoutDots.setVisibility(View.GONE);
        } else {
            // Показываем карусель и кнопки управления
            layoutAddMediaPlaceholder.setVisibility(View.GONE);
            viewPagerMedia.setVisibility(View.VISIBLE);
            layoutMediaControls.setVisibility(View.VISIBLE);

            ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, selectedMediaUris);
            viewPagerMedia.setAdapter(adapter);

            if (selectedMediaUris.size() > 1) {
                tabLayoutDots.setVisibility(View.VISIBLE);
                new TabLayoutMediator(tabLayoutDots, viewPagerMedia, (tab, position) -> {}).attach();
            } else {
                tabLayoutDots.setVisibility(View.GONE);
            }
        }
    }

    private void setupListeners() {
        btnClose.setOnClickListener(v -> onBackPressed());

        // Открытие галереи
        View.OnClickListener openPicker = v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            mediaPickerLauncher.launch(intent);
        };

        // Клик по пустой зоне или кнопке "Добавить еще"
        btnAddMedia.setOnClickListener(v -> {
            if (selectedMediaUris.isEmpty()) openPicker.onClick(v);
        });
        btnAddMoreMedia.setOnClickListener(openPicker);

        // Кнопка очистки
        btnClearMedia.setOnClickListener(v -> {
            selectedMediaUris.clear();
            updateMediaUI();
        });

        btnSelectLocation.setOnClickListener(v -> {
            Intent intent = new Intent(CreatePostActivity.this, PickLocationActivity.class);
            locationPickerLauncher.launch(intent);
        });

        // Логика свитча приватности
        switchPrivacy.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                ivPrivacyIcon.setImageResource(R.drawable.ic_public);
                tvPrivacyText.setText("Публично");
                ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.online_indicator));
            } else {
                ivPrivacyIcon.setImageResource(R.drawable.ic_lock);
                tvPrivacyText.setText("Приватно");
                ivPrivacyIcon.setColorFilter(ContextCompat.getColor(this, R.color.accent));
            }
        });

        btnSavePostTop.setOnClickListener(v -> validateAndUpload());
    }

    private void validateAndUpload() {
        String title = inputTitle.getText().toString().trim();
        String desc = inputDescription.getText().toString().trim();
        boolean isPublic = switchPrivacy.isChecked();

        if (title.isEmpty()) {
            inputTitle.setError("Введите название");
            return;
        }
        if (selectedMediaUris.isEmpty()) {
            Toast.makeText(this, "Пожалуйста, выберите хотя бы одно фото", Toast.LENGTH_SHORT).show();
            return;
        }

        if (NetworkUtils.isConnected(this)) {
            progressDialog.show();
            uploadToCloudinary(title, desc, isPublic);
        } else {
            savePostOffline(title, desc, isPublic);
        }
    }

    private void savePostOffline(String title, String desc, boolean isPublic) {
        new Thread(() -> {
            try {
                File localFile = copyUriToInternalStorage(selectedMediaUris.get(0));
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
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest uploadWork = new OneTimeWorkRequest.Builder(UploadWorker.class).setConstraints(constraints).build();
        WorkManager.getInstance(this).enqueueUniqueWork("UPLOAD_POSTS_WORK", ExistingWorkPolicy.APPEND_OR_REPLACE, uploadWork);
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

    private void uploadToCloudinary(String title, String desc, boolean isPublic) {
        List<String> uploadedUrls = new ArrayList<>();
        AtomicInteger uploadCount = new AtomicInteger(0);

        for (Uri uri : selectedMediaUris) {
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    InputStream inputStream = getCompressedImageStream(uri);
                    Map<String, Object> uploadParams = new HashMap<>();
                    uploadParams.put("resource_type", "image");

                    Map uploadResult = cloudinary.uploader().upload(inputStream, uploadParams);
                    String uploadedUrl = (String) uploadResult.get("secure_url");

                    synchronized (uploadedUrls) {
                        uploadedUrls.add(uploadedUrl);
                    }

                    if (uploadCount.incrementAndGet() == selectedMediaUris.size()) {
                        runOnUiThread(() -> savePostToFirebase(title, desc, isPublic, uploadedUrls));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Ошибка загрузки фото", Toast.LENGTH_LONG).show();
                    });
                }
            });
        }
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

    private void savePostToFirebase(String title, String desc, boolean isPublic, List<String> mediaUrls) {
        String postId = postsRef.push().getKey();
        String userId = mAuth.getCurrentUser() != null ? mAuth.getCurrentUser().getUid() : "anon";
        String mainUrl = mediaUrls.isEmpty() ? "" : mediaUrls.get(0);

        Post newPost = new Post(
                postId, userId, title, desc, mainUrl, mediaUrls,
                selectedMediaType, selectedLat, selectedLng,
                isPublic, System.currentTimeMillis()
        );

        if (postId != null) {
            postsRef.child(postId).setValue(newPost)
                    .addOnSuccessListener(aVoid -> {
                        // УВЕЛИЧИВАЕМ СЧЕТЧИК ПОСТОВ В ПРОФИЛЕ
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(userId).child("memoriesCount")
                                .setValue(com.google.firebase.database.ServerValue.increment(1));

                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Опубликовано!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        Toast.makeText(CreatePostActivity.this, "Ошибка сохранения", Toast.LENGTH_SHORT).show();
                    });
        }
    }

    @Override
    public void onBackPressed() {
        if (getIntent().hasExtra("revealX")) {
            int revealX = getIntent().getIntExtra("revealX", 0);
            int revealY = getIntent().getIntExtra("revealY", 0);
            float finalRadius = (float) (Math.max(rootLayout.getWidth(), rootLayout.getHeight()) * 1.1);
            Animator circularReveal = ViewAnimationUtils.createCircularReveal(rootLayout, revealX, revealY, finalRadius, 0);
            circularReveal.setDuration(400);
            circularReveal.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    rootLayout.setVisibility(View.INVISIBLE);
                    finish();
                    overridePendingTransition(0, 0);
                }
            });
            circularReveal.start();
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
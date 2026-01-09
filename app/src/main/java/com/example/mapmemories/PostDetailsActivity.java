package com.example.mapmemories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class PostDetailsActivity extends AppCompatActivity {

    private String postId;
    private DatabaseReference postRef;

    private ImageView detailImage;
    private EditText editTitle, editDescription, editLatitude, editLongitude;
    private SwitchMaterial switchPrivacy;
    private MaterialButton btnSave, btnDelete;
    private ImageButton btnBack;
    private ImageButton btnPickOnMap;

    private View mainContentScroll;

    // Элементы ZOOM
    private FrameLayout expandedContainer;
    private ImageView expandedImage;
    private View expandedBackground;
    private LinearLayout expandedControls;
    private MaterialButton btnChangeExpanded, btnDownloadExpanded;
    private Animator currentAnimator;
    private long shortAnimationDuration = 200;

    // Cloudinary и Фото
    private Cloudinary cloudinary;
    private String currentMediaUrl = "";
    private ActivityResultLauncher<Intent> locationPickerLauncher;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_post_details);

        postId = getIntent().getStringExtra("postId");
        if (postId == null) {
            Toast.makeText(this, "Ошибка: ID поста не найден", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initCloudinary();
        postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);

        initViews();
        setupLaunchers();
        loadPostData();
        setupClickListeners();
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    private void initViews() {
        // Основные элементы
        detailImage = findViewById(R.id.detailImage);
        editTitle = findViewById(R.id.editTitle);
        editDescription = findViewById(R.id.editDescription);
        editLatitude = findViewById(R.id.editLatitude);
        editLongitude = findViewById(R.id.editLongitude);
        switchPrivacy = findViewById(R.id.switchPrivacy);
        btnPickOnMap = findViewById(R.id.btnPickOnMap);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBackDetails);

        mainContentScroll = findViewById(R.id.mainContentScroll);

        // Zoom элементы
        expandedContainer = findViewById(R.id.expandedContainer);
        expandedImage = findViewById(R.id.expandedImage);
        expandedBackground = findViewById(R.id.expandedBackground);
        expandedControls = findViewById(R.id.expandedControls);
        btnChangeExpanded = findViewById(R.id.btnChangeExpanded);
        btnDownloadExpanded = findViewById(R.id.btnDownloadExpanded);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Обработка...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveChanges());
        btnDelete.setOnClickListener(v -> confirmDelete());

        btnPickOnMap.setOnClickListener(v -> {
            double currentLat = 0;
            double currentLng = 0;
            try {
                String latStr = editLatitude.getText().toString().replace(",", ".");
                String lngStr = editLongitude.getText().toString().replace(",", ".");
                if (!latStr.isEmpty()) currentLat = Double.parseDouble(latStr);
                if (!lngStr.isEmpty()) currentLng = Double.parseDouble(lngStr);
            } catch (NumberFormatException e) {}

            Intent intent = new Intent(PostDetailsActivity.this, PickLocationActivity.class);
            intent.putExtra("lat", currentLat);
            intent.putExtra("lng", currentLng);
            locationPickerLauncher.launch(intent);
        });

        // Нажатие на картинку -> ЗУМ
        detailImage.setOnClickListener(v -> zoomImageFromThumb(detailImage));

        // Кнопки внутри зума
        btnDownloadExpanded.setOnClickListener(v -> {
            if (!currentMediaUrl.isEmpty()) {
                DownloadHelper.downloadImage(this, currentMediaUrl);
            }
        });

        btnChangeExpanded.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            imagePickerLauncher.launch(intent);
        });

        // Закрытие зума по клику на фон
        expandedBackground.setOnClickListener(v -> closeZoom());
        expandedImage.setOnClickListener(v -> closeZoom());
    }

    private void setupLaunchers() {
        locationPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        double lat = result.getData().getDoubleExtra("lat", 0.0);
                        double lng = result.getData().getDoubleExtra("lng", 0.0);
                        editLatitude.setText(String.valueOf(lat));
                        editLongitude.setText(String.valueOf(lng));
                        Toast.makeText(this, "Координаты обновлены", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) {
                            uploadImageToCloudinary(imageUri);
                        }
                    }
                }
        );
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        progressDialog.setMessage("Загрузка новой картинки...");
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String newUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    currentMediaUrl = newUrl;
                    // Обновляем UI
                    Glide.with(this).load(currentMediaUrl).into(detailImage);
                    Glide.with(this).load(currentMediaUrl).into(expandedImage);
                    progressDialog.dismiss();
                    Toast.makeText(this, "Картинка обновлена (нажмите Сохранить)", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void loadPostData() {
        progressDialog.show();
        postRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                progressDialog.dismiss();
                Post post = snapshot.getValue(Post.class);
                if (post != null) {
                    editTitle.setText(post.getTitle());
                    editDescription.setText(post.getDescription());
                    switchPrivacy.setChecked(post.isPublic());
                    editLatitude.setText(String.valueOf(post.getLatitude()));
                    editLongitude.setText(String.valueOf(post.getLongitude()));

                    if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
                        currentMediaUrl = post.getMediaUrl();
                        Glide.with(PostDetailsActivity.this)
                                .load(currentMediaUrl)
                                .into(detailImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(PostDetailsActivity.this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void zoomImageFromThumb(final View thumbView) {
        if (currentAnimator != null) currentAnimator.cancel();



        Glide.with(this).load(currentMediaUrl).into(expandedImage);

        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        thumbView.getGlobalVisibleRect(startBounds);
        findViewById(android.R.id.content).getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        float startScale;
        if ((float) finalBounds.width() / finalBounds.height() > (float) startBounds.width() / startBounds.height()) {
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        thumbView.setAlpha(0f);
        expandedContainer.setVisibility(View.VISIBLE);

        expandedImage.setPivotX(0f);
        expandedImage.setPivotY(0f);

        AnimatorSet set = new AnimatorSet();
        set.play(ObjectAnimator.ofFloat(expandedImage, View.X, startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f, 1f))
                .with(ObjectAnimator.ofFloat(expandedControls, View.ALPHA, 0f, 1f));

        set.setDuration(shortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAnimator = null;
            }
        });
        set.start();
        currentAnimator = set;

        // БЛЮР
        ViewGroup root = findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentScroll.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
        }
    }

    private void closeZoom() {
        if (currentAnimator != null) currentAnimator.cancel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentScroll.setRenderEffect(null);
        }

        expandedContainer.setVisibility(View.GONE);
        detailImage.setAlpha(1f);
    }

    @Override
    public void onBackPressed() {
        if (expandedContainer.getVisibility() == View.VISIBLE) {
            closeZoom();
        } else {
            super.onBackPressed();
        }
    }

    private void saveChanges() {
        String newTitle = editTitle.getText().toString().trim();
        String newDesc = editDescription.getText().toString().trim();
        boolean isPublic = switchPrivacy.isChecked();
        double newLat = 0;
        double newLng = 0;
        try {
            newLat = Double.parseDouble(editLatitude.getText().toString().replace(",", "."));
            newLng = Double.parseDouble(editLongitude.getText().toString().replace(",", "."));
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Неверный формат координат", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newTitle.isEmpty()) {
            editTitle.setError("Введите название");
            return;
        }

        progressDialog.setMessage("Сохранение...");
        progressDialog.show();

        Map<String, Object> updates = new HashMap<>();
        updates.put("title", newTitle);
        updates.put("description", newDesc);
        updates.put("public", isPublic);
        updates.put("latitude", newLat);
        updates.put("longitude", newLng);
        updates.put("mediaUrl", currentMediaUrl); // Сохраняем и ссылку на фото

        postRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(PostDetailsActivity.this, "Сохранено!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(PostDetailsActivity.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Удаление")
                .setMessage("Вы уверены, что хотите удалить это воспоминание?")
                .setPositiveButton("Удалить", (dialog, which) -> deletePost())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void deletePost() {
        progressDialog.setMessage("Удаление...");
        progressDialog.show();
        postRef.removeValue()
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(PostDetailsActivity.this, "Удалено", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(PostDetailsActivity.this, "Ошибка удаления", Toast.LENGTH_SHORT).show();
                });
    }
}

/*
<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/primary">

    <ScrollView
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fillViewport="true"
        android:id="@+id/mainContentScroll"
        android:fitsSystemWindows="true">

        <LinearLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:orientation="vertical"
            android:paddingBottom="30dp">

            <!-- Картинка на весь верх -->
            <FrameLayout
                android:layout_width="match_parent"
                android:layout_height="300dp">

                <ImageView
                    android:id="@+id/detailImage"
                    android:layout_width="match_parent"
                    android:layout_height="match_parent"
                    android:scaleType="centerCrop"
                    android:src="@color/secondary" />

                <View
                    android:layout_width="match_parent"
                    android:layout_height="80dp"
                    android:background="@drawable/gradient_bottom"
                    android:visibility="gone"/>

                <ImageButton
                    android:id="@+id/btnBackDetails"
                    android:layout_width="48dp"
                    android:layout_height="48dp"
                    android:layout_margin="16dp"
                    android:background="@drawable/rounded_button"
                    android:src="@drawable/ic_arrow_back"
                    app:tint="@color/text_primary"
                    android:elevation="4dp"/>
            </FrameLayout>

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:padding="20dp">

                <!-- Название -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Название"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp" />

                <EditText
                    android:id="@+id/editTitle"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="@android:color/transparent"
                    android:hint="Введите название"
                    android:paddingVertical="8dp"
                    android:textColor="@color/text_primary"
                    android:textSize="22sp"
                    android:textStyle="bold" />

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="@color/text_secondary"
                    android:alpha="0.3"
                    android:layout_marginBottom="16dp"/>

                <!-- Описание -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Описание"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp" />

                <EditText
                    android:id="@+id/editDescription"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:background="@android:color/transparent"
                    android:gravity="top"
                    android:hint="Опишите воспоминание..."
                    android:minLines="3"
                    android:paddingVertical="8dp"
                    android:textColor="@color/text_primary"
                    android:textSize="16sp" />

                <View
                    android:layout_width="match_parent"
                    android:layout_height="1dp"
                    android:background="@color/text_secondary"
                    android:alpha="0.3"
                    android:layout_marginVertical="16dp"/>

                <!-- Настройки приватности -->
                <com.google.android.material.switchmaterial.SwitchMaterial
                    android:id="@+id/switchPrivacy"
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:text="Публичное воспоминание"
                    android:textColor="@color/text_primary"
                    android:textSize="16sp"
                    app:thumbTint="@color/accent"
                    app:trackTint="@color/text_secondary" />

                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Если включено, метку видят все пользователи"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginBottom="16dp"/>

                <!-- Редактирование координат -->
                <TextView
                    android:layout_width="wrap_content"
                    android:layout_height="wrap_content"
                    android:text="Координаты (Широта / Долгота)"
                    android:textColor="@color/text_secondary"
                    android:textSize="12sp"
                    android:layout_marginBottom="8dp"/>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:layout_marginBottom="24dp">

                    <EditText
                        android:id="@+id/editLatitude"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:hint="Lat"
                        android:inputType="numberDecimal|numberSigned"
                        android:textColor="@color/text_primary"
                        android:layout_marginEnd="8dp"/>

                    <EditText
                        android:id="@+id/editLongitude"
                        android:layout_width="0dp"
                        android:layout_height="wrap_content"
                        android:layout_weight="1"
                        android:hint="Lng"
                        android:inputType="numberDecimal|numberSigned"
                        android:textColor="@color/text_primary" />

                    <ImageButton
                        android:id="@+id/btnPickOnMap"
                        android:layout_width="48dp"
                        android:layout_height="48dp"
                        android:layout_marginStart="8dp"
                        android:background="@drawable/rounded_button"
                        android:backgroundTint="@color/surface_dark"
                        app:tint="@color/online_indicator"
                        android:src="@drawable/ic_my_location"
                        android:scaleType="centerInside"
                        android:padding="12dp" />

                </LinearLayout>

                <!-- Кнопки действий -->
                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnSave"
                    android:layout_width="match_parent"
                    android:layout_height="60dp"
                    android:text="Сохранить изменения"
                    app:cornerRadius="12dp"
                    android:layout_marginBottom="12dp"
                    app:backgroundTint="@color/accent"/>

                <com.google.android.material.button.MaterialButton
                    android:id="@+id/btnDelete"
                    android:layout_width="match_parent"
                    android:layout_height="60dp"
                    android:text="Удалить воспоминание"
                    android:textColor="@android:color/holo_red_light"
                    app:strokeColor="@android:color/holo_red_light"
                    app:strokeWidth="1dp"
                    app:cornerRadius="12dp"
                    app:backgroundTint="@android:color/transparent"
                    style="@style/Widget.MaterialComponents.Button.OutlinedButton"/>

            </LinearLayout>
        </LinearLayout>
    </ScrollView>

    <FrameLayout
        android:id="@+id/expandedContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        android:clickable="true"
        android:focusable="true"
        android:elevation="100dp">

        <View
            android:id="@+id/expandedBackground"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="#E6000000"
            android:alpha="0" />

        <ImageView
            android:id="@+id/expandedImage"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_margin="0dp"
            android:scaleType="fitCenter"
            android:src="@color/secondary" />

        <LinearLayout
            android:id="@+id/expandedControls"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_gravity="bottom|center_horizontal"
            android:layout_marginBottom="64dp"
            android:orientation="horizontal"
            android:alpha="0">

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnChangeExpanded"
                android:layout_width="wrap_content"
                android:layout_height="60dp"
                android:text="Заменить"
                android:textColor="@android:color/white"
                app:icon="@drawable/ic_edit"
                app:iconTint="@android:color/white"
                app:backgroundTint="@color/secondary"
                app:cornerRadius="30dp"
                android:layout_marginEnd="16dp"/>

            <com.google.android.material.button.MaterialButton
                android:id="@+id/btnDownloadExpanded"
                android:layout_width="wrap_content"
                android:layout_height="60dp"
                android:text="Скачать"
                android:textColor="@color/text_primary"
                app:icon="@drawable/ic_download"
                app:iconTint="@color/text_primary"
                app:backgroundTint="@color/secondary"
                app:cornerRadius="30dp"/>

        </LinearLayout>
    </FrameLayout>
</FrameLayout>
 */
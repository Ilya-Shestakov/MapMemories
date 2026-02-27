package com.example.mapmemories.Post;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Lenta.PickLocationActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.DownloadHelper;
import com.example.mapmemories.systemHelpers.ImageCarouselAdapter;
import com.example.mapmemories.systemHelpers.Post;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PostDetailsActivity extends AppCompatActivity {

    private String postId;
    private DatabaseReference postRef;

    private EditText editTitle, editDescription, editLatitude, editLongitude;
    private SwitchMaterial switchPrivacy;
    private MaterialButton btnSave, btnDelete;
    private ImageButton btnBack, btnPickOnMap;

    private ViewPager2 viewPagerMedia;
    private TabLayout tabLayoutDots;
    private View mainContentLayout;

    private FrameLayout expandedContainer;
    private PhotoView expandedImage;
    private MaterialButton btnDownloadExpanded;
    private String currentZoomedUrl = "";

    private ActivityResultLauncher<Intent> locationPickerLauncher;
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

        postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);

        initViews();
        setupLaunchers();
        loadPostData();
        setupClickListeners();
    }

    private void initViews() {
        editTitle = findViewById(R.id.editTitle);
        editDescription = findViewById(R.id.editDescription);
        editLatitude = findViewById(R.id.editLatitude);
        editLongitude = findViewById(R.id.editLongitude);
        switchPrivacy = findViewById(R.id.switchPrivacy);
        btnPickOnMap = findViewById(R.id.btnPickOnMap);
        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBackDetails);

        viewPagerMedia = findViewById(R.id.viewPagerMedia);
        tabLayoutDots = findViewById(R.id.tabLayoutDots);
        mainContentLayout = findViewById(R.id.mainContentLayout);

        expandedContainer = findViewById(R.id.expandedContainer);
        expandedImage = findViewById(R.id.expandedImage);
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

        btnDownloadExpanded.setOnClickListener(v -> {
            if (!currentZoomedUrl.isEmpty()) {
                DownloadHelper.downloadImage(this, currentZoomedUrl);
            }
        });

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
                    }
                }
        );
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

                    List<String> urls = post.getMediaUrls();
                    if (urls == null || urls.isEmpty()) {
                        urls = new ArrayList<>();
                        if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
                            urls.add(post.getMediaUrl());
                        }
                    }

                    if (!urls.isEmpty()) {
                        ImageCarouselAdapter adapter = new ImageCarouselAdapter(PostDetailsActivity.this, urls, (pos, url) -> openFullScreenZoom(url));
                        viewPagerMedia.setAdapter(adapter);

                        if (urls.size() > 1) {
                            tabLayoutDots.setVisibility(View.VISIBLE);
                            new TabLayoutMediator(tabLayoutDots, viewPagerMedia, (tab, pos) -> {}).attach();
                        } else {
                            tabLayoutDots.setVisibility(View.GONE);
                        }
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

    private void openFullScreenZoom(String imageUrl) {
        currentZoomedUrl = imageUrl;
        expandedContainer.setVisibility(View.VISIBLE);
        expandedContainer.setAlpha(0f);
        expandedContainer.animate().alpha(1f).setDuration(200).start();

        Glide.with(this).load(imageUrl).into(expandedImage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
        }
    }

    private void closeZoom() {
        expandedContainer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            expandedContainer.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(null);
            }
        }).start();
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

        postRef.updateChildren(updates)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(PostDetailsActivity.this, "Сохранено!", Toast.LENGTH_SHORT).show();
                    finish();
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
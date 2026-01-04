package com.example.mapmemories;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class PostDetailsActivity extends AppCompatActivity {

    private String postId;
    private DatabaseReference postRef;

    private ImageView detailImage;
    private EditText editTitle, editDescription, editLatitude, editLongitude;
    private SwitchMaterial switchPrivacy;
    private MaterialButton btnSave, btnDelete;
    private ImageButton btnBack;
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
        loadPostData();

        btnBack.setOnClickListener(v -> finish());
        btnSave.setOnClickListener(v -> saveChanges());
        btnDelete.setOnClickListener(v -> confirmDelete());
    }

    private void initViews() {
        detailImage = findViewById(R.id.detailImage);
        editTitle = findViewById(R.id.editTitle);
        editDescription = findViewById(R.id.editDescription);
        editLatitude = findViewById(R.id.editLatitude);
        editLongitude = findViewById(R.id.editLongitude);
        switchPrivacy = findViewById(R.id.switchPrivacy);

        btnSave = findViewById(R.id.btnSave);
        btnDelete = findViewById(R.id.btnDelete);
        btnBack = findViewById(R.id.btnBackDetails);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Обработка...");
        progressDialog.setCancelable(false);
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

                    // Загружаем приватность
                    switchPrivacy.setChecked(post.isPublic());

                    // Загружаем координаты
                    editLatitude.setText(String.valueOf(post.getLatitude()));
                    editLongitude.setText(String.valueOf(post.getLongitude()));

                    if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
                        Glide.with(PostDetailsActivity.this)
                                .load(post.getMediaUrl())
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

    private void saveChanges() {
        String newTitle = editTitle.getText().toString().trim();
        String newDesc = editDescription.getText().toString().trim();
        boolean isPublic = switchPrivacy.isChecked();

        // Парсинг координат
        double newLat = 0;
        double newLng = 0;
        try {
            // Заменяем запятую на точку, если пользователь ввел с запятой
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
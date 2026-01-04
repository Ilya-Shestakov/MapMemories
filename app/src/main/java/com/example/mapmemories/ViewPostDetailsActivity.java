package com.example.mapmemories;

import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Date;
import java.util.Locale;

public class ViewPostDetailsActivity extends AppCompatActivity {

    private String postId;

    // UI
    private ImageView detailImage, detailVideoIcon, detailAuthorAvatar;
    private TextView detailAuthorName, detailDate, detailTitle, detailCoordinates, detailDescription;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_post_details);

        // Получаем ID поста
        postId = getIntent().getStringExtra("postId");
        if (postId == null) {
            Toast.makeText(this, "Ошибка: пост не найден", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadPostData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        toolbar.setNavigationOnClickListener(v -> finish());

        detailImage = findViewById(R.id.detailImage);
        detailVideoIcon = findViewById(R.id.detailVideoIcon);
        detailAuthorAvatar = findViewById(R.id.detailAuthorAvatar);
        detailAuthorName = findViewById(R.id.detailAuthorName);
        detailDate = findViewById(R.id.detailDate);
        detailTitle = findViewById(R.id.detailTitle);
        detailCoordinates = findViewById(R.id.detailCoordinates);
        detailDescription = findViewById(R.id.detailDescription);
    }

    private void loadPostData() {
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);

        postRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Post post = snapshot.getValue(Post.class);
                if (post != null) {
                    updateUI(post);
                    loadAuthorInfo(post.getUserId());
                } else {
                    Toast.makeText(ViewPostDetailsActivity.this, "Пост был удален", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ViewPostDetailsActivity.this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(Post post) {
        detailTitle.setText(post.getTitle());
        detailDescription.setText(post.getDescription());

        String dateString = DateFormat.format("dd MMMM yyyy, HH:mm", new Date(post.getTimestamp())).toString();
        detailDate.setText(dateString);

        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        detailCoordinates.setText(coords);

        if (!TextUtils.isEmpty(post.getMediaUrl())) {
            Glide.with(this).load(post.getMediaUrl()).into(detailImage);
        }

        if ("video".equals(post.getMediaType())) {
            detailVideoIcon.setVisibility(View.VISIBLE);
        } else {
            detailVideoIcon.setVisibility(View.GONE);
        }
    }

    private void loadAuthorInfo(String userId) {
        if (userId == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("username").getValue(String.class);
                    String avatar = snapshot.child("profileImageUrl").getValue(String.class);

                    detailAuthorName.setText(TextUtils.isEmpty(name) ? "Неизвестный" : name);

                    if (!TextUtils.isEmpty(avatar)) {
                        Glide.with(ViewPostDetailsActivity.this)
                                .load(avatar)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(detailAuthorAvatar);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
package com.example.mapmemories;

import android.content.Intent;
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
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class UserProfileActivity extends AppCompatActivity {

    private String targetUserId; // ID пользователя, чей профиль смотрим

    private ImageView profileImage;
    private TextView profileUsername, profileAbout, profileJoinDate, countMemories, countLikes;
    private RecyclerView userPostsRecyclerView;

    private DatabaseReference userRef;
    private DatabaseReference postsRef;

    private PublicMemoriesAdapter adapter;
    private List<Post> userPostList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        // Получаем ID пользователя, переданный через Intent
        targetUserId = getIntent().getStringExtra("targetUserId");

        if (TextUtils.isEmpty(targetUserId)) {
            Toast.makeText(this, "Пользователь не найден", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        setupFirebase();
        loadUserInfo();
        setupRecyclerView();
        loadUserPublicPosts();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        profileImage = findViewById(R.id.profileImage);
        profileUsername = findViewById(R.id.profileUsername);
        profileAbout = findViewById(R.id.profileAbout);
        profileJoinDate = findViewById(R.id.profileJoinDate);
        countMemories = findViewById(R.id.countMemories);
        countLikes = findViewById(R.id.countLikes);
        userPostsRecyclerView = findViewById(R.id.userPostsRecyclerView);
    }

    private void setupFirebase() {
        userRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId);
        postsRef = FirebaseDatabase.getInstance().getReference("posts");
    }

    private void loadUserInfo() {
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Парсим данные из JSON структуры
                    String username = snapshot.child("username").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    String imageUrl = snapshot.child("profileImageUrl").getValue(String.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    // Статистика (если есть в БД как int/long)
                    Object memCountObj = snapshot.child("memoriesCount").getValue();
                    Object likesCountObj = snapshot.child("likesCount").getValue();

                    // Установка данных
                    profileUsername.setText(TextUtils.isEmpty(username) ? "Без имени" : username);
                    profileAbout.setText(TextUtils.isEmpty(about) ? "" : about);
                    profileAbout.setVisibility(TextUtils.isEmpty(about) ? View.GONE : View.VISIBLE);

                    if (joinDate != null) {
                        String date = DateFormat.format("dd.MM.yyyy", new Date(joinDate)).toString();
                        profileJoinDate.setText("На сайте с: " + date);
                    }

                    countMemories.setText(String.valueOf(memCountObj != null ? memCountObj : "0"));
                    countLikes.setText(String.valueOf(likesCountObj != null ? likesCountObj : "0"));

                    if (!TextUtils.isEmpty(imageUrl)) {
                        Glide.with(UserProfileActivity.this)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(profileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(UserProfileActivity.this, "Ошибка загрузки профиля", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupRecyclerView() {
        userPostsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userPostList = new ArrayList<>();

        // Используем тот же адаптер, что и в ленте
        adapter = new PublicMemoriesAdapter(this, userPostList, post -> {
            Intent intent = new Intent(UserProfileActivity.this, ViewPostDetailsActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });

        userPostsRecyclerView.setAdapter(adapter);
    }

    private void loadUserPublicPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userPostList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot postSnap : snapshot.getChildren()) {
                        Post post = postSnap.getValue(Post.class);

                        // ФИЛЬТР:
                        // 1. Пост принадлежит этому пользователю
                        // 2. Пост публичный
                        if (post != null
                                && targetUserId.equals(post.getUserId())
                                && post.isPublic()) {

                            userPostList.add(post);
                        }
                    }
                    // Сортировка: новые сверху
                    Collections.reverse(userPostList);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
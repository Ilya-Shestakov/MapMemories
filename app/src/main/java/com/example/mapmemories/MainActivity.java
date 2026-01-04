package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView memoriesRecyclerView;
    private FloatingActionButton fabAdd, fabMap;
    private ImageView logoutButton;
    private ImageView profileButton;

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private DatabaseReference postsRef;

    // Адаптер и список
    private PublicMemoriesAdapter publicAdapter;
    private List<Post> publicPostList;

    // Для двойного нажатия "Назад"
    private long backPressedTime;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration.getInstance().load(this,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

        mAuth = FirebaseAuth.getInstance();
        postsRef = FirebaseDatabase.getInstance().getReference("posts"); // Инициализируем ссылку на посты

        checkCurrentUser();
        initViews();
        setupClickListeners();
        setupRecyclerView();

        // Настройка выхода по двойному клику
        setupDoubleBackExit();

        // Загрузка фото (твоя существующая логика)
        loadUserAvatar();

        // Загрузка публичных постов (НОВОЕ)
        loadPublicPosts();
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        }
    }

    private void initViews() {
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        fabMap = findViewById(R.id.fabMap);
        profileButton = findViewById(R.id.profileButton);
        logoutButton = findViewById(R.id.logoutButton);
    }

    private void setupDoubleBackExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    if (backToast != null) backToast.cancel();
                    finish();
                    finishAffinity();
                } else {
                    backToast = Toast.makeText(getBaseContext(), "Нажмите еще раз, чтобы выйти", Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    private void loadUserAvatar() {
        if (userRef == null) return;
        userRef.child("profileImageUrl").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String profileImageUrl = snapshot.getValue(String.class);
                if (!TextUtils.isEmpty(profileImageUrl)) {
                    Glide.with(MainActivity.this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder)
                            .circleCrop()
                            .into(profileButton);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupRecyclerView() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        publicPostList = new ArrayList<>();

        publicAdapter = new PublicMemoriesAdapter(this, publicPostList, post -> {
            FirebaseUser currentUser = mAuth.getCurrentUser();

            if (currentUser != null && post.getUserId().equals(currentUser.getUid())) {
                Intent intent = new Intent(MainActivity.this, PostDetailsActivity.class);
                intent.putExtra("postId", post.getId());
                intent.putExtra("isEditMode", true);
                startActivity(intent);
            } else {
                Intent intent = new Intent(MainActivity.this, ViewPostDetailsActivity.class);
                intent.putExtra("postId", post.getId());
                startActivity(intent);
            }
        });

        memoriesRecyclerView.setAdapter(publicAdapter);
    }

    // Метод загрузки постов
    private void loadPublicPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                publicPostList.clear();

                if (snapshot.exists()) {
                    for (DataSnapshot postSnap : snapshot.getChildren()) {
                        Post post = postSnap.getValue(Post.class);

                        // ФИЛЬТРАЦИЯ: Добавляем только если пост публичный
                        if (post != null && post.isPublic()) {
                            publicPostList.add(post);
                        }
                    }
                    // Сортируем: сначала новые
                    Collections.reverse(publicPostList);
                }

                publicAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(MainActivity.this, "Ошибка загрузки ленты", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void setupClickListeners() {
        profileButton.setOnClickListener(view -> {
            VibratorHelper.vibrate(MainActivity.this, 50);
            startActivity(new Intent(this, Profile.class));
        });

        logoutButton.setOnClickListener(v -> {
            DialogHelper.showConfirmation(this, "Выход", "Вы уверены, что хотите выйти?", () -> {

                mAuth.signOut();
                startActivity(new Intent(this, LoginActivity.class));
                finish();
            });
        });

        fabAdd.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);

            fabAdd.animate()
                    .rotationBy(360f)
                    .setDuration(2000)
                    .start();

            int revealX = (int) (v.getX() + v.getWidth() / 2);
            int revealY = (int) (v.getY() + v.getHeight() / 2);

            Intent intent = new Intent(this, CreatePostActivity.class);
            intent.putExtra("revealX", revealX);
            intent.putExtra("revealY", revealY);
            startActivity(intent);

            overridePendingTransition(0, 0);
        });

        fabMap.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivity(new Intent(this, Setting.class));
        });
    }

    private void showLogoutConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы уверены, что хотите выйти?")
                .setPositiveButton("Выйти", (dialog, which) -> logoutUser())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void logoutUser() {
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
package com.example.mapmemories.Lenta;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Chats.ChatListActivity;
import com.example.mapmemories.LogRegStart.LoginActivity;
import com.example.mapmemories.Post.CreatePostActivity;
import com.example.mapmemories.systemHelpers.Post;
import com.example.mapmemories.Post.PostDetailsActivity;
import com.example.mapmemories.Profile.Profile;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.example.mapmemories.Post.ViewPostDetailsActivity;
import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.systemHelpers.DialogHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

//import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout;
    private RecyclerView memoriesRecyclerView;
    private ImageView fabMap, fabChats, fabAddIcon;
    private MaterialCardView fabAdd, bottomDock;
    private ImageView profileButton;
    private ImageView fabSettings;
    private ImageView offlineBadge;
    private TextView appTitle;

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
    private boolean isFirstLoad = true;

    // --- ПРИМЕНЕНИЕ МАСШТАБА ТЕКСТА ---
    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences preferences = newBase.getSharedPreferences(Setting.PREFS_NAME, Context.MODE_PRIVATE);
        float scale = preferences.getFloat("text_scale", 1.0f);
        android.content.res.Configuration config = new android.content.res.Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scale;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        org.osmdroid.config.Configuration.getInstance().load(this,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

        mAuth = FirebaseAuth.getInstance();
        postsRef = FirebaseDatabase.getInstance().getReference("posts");

        checkCurrentUser();
        initViews();
        setupClickListeners();
        setupRecyclerView();
        setupSwipeRefresh();
        setupDoubleBackExit();
        loadUserAvatar();
        loadPublicPosts();
        observeOfflinePosts();
        setupSecretGesture();
    }

    // --- ЛОГИКА СТАТУСА (В СЕТИ / БЫЛ НЕДАВНО) ---
    @Override
    protected void onResume() {
        super.onResume();
        updateMyStatus("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        updateMyStatus(System.currentTimeMillis());
    }

    private void updateMyStatus(Object status) {
        if (userRef == null) return;

        SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, MODE_PRIVATE);
        boolean hideOnline = prefs.getBoolean("privacy_hide_online", false);

        DatabaseReference statusRef = userRef.child("status");

        if (hideOnline) {
            statusRef.setValue("hidden");
        } else {
            statusRef.setValue(status);
            // Если пропадет интернет или приложение убьют, Firebase сам запишет время
            statusRef.onDisconnect().setValue(System.currentTimeMillis());
        }
    }
    // ----------------------------------------------

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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        bottomDock = findViewById(R.id.bottomDock);
        fabAdd = findViewById(R.id.fabAdd);
        fabAddIcon = findViewById(R.id.fabAddIcon);
        fabMap = findViewById(R.id.fabMap);
        fabChats = findViewById(R.id.fabChats);
        profileButton = findViewById(R.id.profileButton);
        offlineBadge = findViewById(R.id.offlineBadge);
        appTitle = findViewById(R.id.appTitle);
        fabSettings = findViewById(R.id.fabSettings);
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.accent, R.color.text_primary, R.color.secondary);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                swipeRefreshLayout.setRefreshing(false);
                runLayoutAnimation();
                VibratorHelper.vibrate(MainActivity.this, 30);
            }, 1200);
        });
    }

    private void observeOfflinePosts() {
        AppDatabase.getDatabase(this).offlinePostDao().getAllPostsLive().observe(this, posts -> {
            if (posts != null && !posts.isEmpty()) {
                offlineBadge.setVisibility(View.VISIBLE);
                offlineBadge.animate().alpha(1f).setDuration(500);
            } else {
                offlineBadge.setVisibility(View.GONE);
            }
        });
    }

    private void setupRecyclerView() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        int resId = R.anim.layout_animation_fall_down;
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(this, resId);
        memoriesRecyclerView.setLayoutAnimation(animation);

        memoriesRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (dy > 15 && bottomDock.getTranslationY() == 0) {
                    bottomDock.animate().translationY(bottomDock.getHeight() + 100).setDuration(250).start();
                } else if (dy < -15 && bottomDock.getTranslationY() > 0) {
                    bottomDock.animate().translationY(0).setDuration(250).start();
                }
            }
        });

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

    private void runLayoutAnimation() {
        final LayoutAnimationController controller = AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);
        memoriesRecyclerView.setLayoutAnimation(controller);
        memoriesRecyclerView.getAdapter().notifyDataSetChanged();
        memoriesRecyclerView.scheduleLayoutAnimation();
    }

    private void loadPublicPosts() {
        postsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                publicPostList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot postSnap : snapshot.getChildren()) {
                        Post post = postSnap.getValue(Post.class);
                        if (post != null && post.isPublic()) {
                            publicPostList.add(post);
                        }
                    }
                    Collections.reverse(publicPostList);
                }
                publicAdapter.notifyDataSetChanged();
                if (isFirstLoad) {
                    memoriesRecyclerView.scheduleLayoutAnimation();
                    isFirstLoad = false;
                }
                if (swipeRefreshLayout.isRefreshing()) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (swipeRefreshLayout.isRefreshing()) swipeRefreshLayout.setRefreshing(false);
                Toast.makeText(MainActivity.this, "Ошибка загрузки ленты", Toast.LENGTH_SHORT).show();
            }
        });
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

    private void setupClickListeners() {
        profileButton.setOnClickListener(view -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(Profile.class, view);
        });

        offlineBadge.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            DialogHelper.showOfflineQueue(this, null);
        });

        fabChats.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(ChatListActivity.class, v);
        });

        fabMap.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(Map.class, v);
        });

        fabSettings.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(Setting.class, v);
        });

        fabAdd.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            Drawable icon = fabAddIcon.getDrawable();
            if (icon instanceof Animatable) {
                ((Animatable) icon).start();
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivityWithAnimation(CreatePostActivity.class, bottomDock);
            }, 250);
        });
    }

    private void startActivityWithAnimation(Class<?> targetActivity, View sourceView) {
        int[] location = new int[2];
        sourceView.getLocationOnScreen(location);

        int revealX = location[0] + sourceView.getWidth() / 2;
        int revealY = location[1] + sourceView.getHeight() / 2;

        Intent intent = new Intent(this, targetActivity);
        intent.putExtra("revealX", revealX);
        intent.putExtra("revealY", revealY);
        startActivity(intent);
        overridePendingTransition(0, 0);
    }

    private void setupSecretGesture() {
        appTitle.setOnTouchListener(new View.OnTouchListener() {
            private final Handler handler = new Handler(Looper.getMainLooper());
            private long lastUpTime = 0;
            private static final int DOUBLE_CLICK_DELAY = 400;
            private static final int LONG_PRESS_DURATION = 1000;

            private final Runnable secretAction = () -> {
                VibratorHelper.vibrate(MainActivity.this, 100);
                showSecretDialog();
            };

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        long currentTime = System.currentTimeMillis();
                        if (currentTime - lastUpTime <= DOUBLE_CLICK_DELAY) {
                            handler.postDelayed(secretAction, LONG_PRESS_DURATION);
                        }
                        break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        handler.removeCallbacks(secretAction);
                        lastUpTime = System.currentTimeMillis();
                        break;
                }
                return true;
            }
        });
    }

    private void showSecretDialog() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Пасхалочка")
                .setMessage("Скиньте денег на сервера...\n\nРазработчик: +7(912)702-36-64. (тинь) \n\nМодерация: +7(996)045-85-29. (сбер)")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setCancelable(true)
                .show();
    }
}
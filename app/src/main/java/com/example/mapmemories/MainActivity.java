package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.ImageView;
import android.widget.Toast;

import com.example.mapmemories.database.AppDatabase;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private SwipeRefreshLayout swipeRefreshLayout; // Добавили
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

    private ImageView offlineBadge;

    private boolean isFirstLoad = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration.getInstance().load(this,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

        mAuth = FirebaseAuth.getInstance();
        postsRef = FirebaseDatabase.getInstance().getReference("posts");

        checkCurrentUser();
        initViews();
        setupClickListeners();
        setupRecyclerView();
        setupSwipeRefresh(); // НОВЫЙ МЕТОД

        setupDoubleBackExit();
        loadUserAvatar();
        loadPublicPosts();
        observeOfflinePosts();

        setupSecretGesture();
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
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout); // Инициализация
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        fabMap = findViewById(R.id.fabMap);
        profileButton = findViewById(R.id.profileButton);
        logoutButton = findViewById(R.id.logoutButton);
        offlineBadge = findViewById(R.id.offlineBadge);
    }

    private void setupSwipeRefresh() {
        // Настраиваем цвета кружка (сделай под свои цвета theme colors)
        swipeRefreshLayout.setColorSchemeResources(
                R.color.accent,
                R.color.text_primary,
                R.color.secondary
        );

        // Логика "потяни вниз"
        swipeRefreshLayout.setOnRefreshListener(() -> {
            // Фейковая задержка для красоты (1.5 секунды)
            new Handler(Looper.getMainLooper()).postDelayed(() -> {

                // 1. Останавливаем кружок
                swipeRefreshLayout.setRefreshing(false);

                // 2. Перезапускаем анимацию списка (то самое "проседание")
                runLayoutAnimation();

                // 3. Можно вибру добавить для кайфа
                VibratorHelper.vibrate(MainActivity.this, 30);

            }, 1200);
        });
    }

    private void observeOfflinePosts() {
        AppDatabase.getDatabase(this).offlinePostDao().getAllPostsLive().observe(this, posts -> {
            if (posts != null && !posts.isEmpty()) {
                offlineBadge.setVisibility(View.VISIBLE);
                // Можно добавить анимацию мигания, чтобы привлекало внимание
                offlineBadge.animate().alpha(1f).setDuration(500);
            } else {
                offlineBadge.setVisibility(View.GONE);
            }
        });
    }

    private void setupRecyclerView() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Привязываем контроллер анимации
        int resId = R.anim.layout_animation_fall_down;
        LayoutAnimationController animation = AnimationUtils.loadLayoutAnimation(this, resId);
        memoriesRecyclerView.setLayoutAnimation(animation);

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

    // Вспомогательный метод для запуска анимации
    private void runLayoutAnimation() {
        final LayoutAnimationController controller =
                AnimationUtils.loadLayoutAnimation(this, R.anim.layout_animation_fall_down);

        memoriesRecyclerView.setLayoutAnimation(controller);
        memoriesRecyclerView.getAdapter().notifyDataSetChanged();
        memoriesRecyclerView.scheduleLayoutAnimation();
    }

    private void loadPublicPosts() {
        // Показываем кружок при первой загрузке (если хочешь)
        // swipeRefreshLayout.setRefreshing(true);

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

    // ... Остальные методы (setupDoubleBackExit, loadUserAvatar, setupClickListeners, logoutUser) без изменений ...

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
            VibratorHelper.vibrate(MainActivity.this, 50);
            int revealX = (int) (view.getX() + view.getWidth() / 2);
            int revealY = (int) (view.getY() + view.getHeight() / 2);
            Intent intent = new Intent(this, Profile.class);
            intent.putExtra("revealX", revealX);
            intent.putExtra("revealY", revealY);
            startActivity(intent);
            overridePendingTransition(0, 0);
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
            fabAdd.animate().rotationBy(180f).setDuration(300).start();
            new android.os.Handler().postDelayed(() -> {
                int revealX = (int) (v.getX() + v.getWidth() / 2);
                int revealY = (int) (v.getY() + v.getHeight() / 2);
                Intent intent = new Intent(this, CreatePostActivity.class);
                intent.putExtra("revealX", revealX);
                intent.putExtra("revealY", revealY);
                startActivity(intent);
                overridePendingTransition(0, 0);
            }, 250);
        });

        fabMap.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivity(new Intent(this, Setting.class));
        });
    }

    private void setupSecretGesture() {
        TextView appTitle = findViewById(R.id.appTitle);

        appTitle.setOnTouchListener(new View.OnTouchListener() {
            private Handler handler = new Handler(Looper.getMainLooper());
            private long lastUpTime = 0; // Время, когда палец был поднят в последний раз

            private static final int DOUBLE_CLICK_DELAY = 400;
            private static final int LONG_PRESS_DURATION = 1000;

            private Runnable secretAction = () -> {
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
        // Самый обычный, системный AlertDialog без дизайна
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Пасхалочка")
                .setMessage("Скиньте денег на сервера...\n\nРазработчик: +7(912)702-36-64.\n\nМодерация: +7(996)045-85-29")
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Отмена", null)
                .setCancelable(true)
                .show();
    }

}
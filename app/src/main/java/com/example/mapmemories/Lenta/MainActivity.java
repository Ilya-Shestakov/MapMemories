package com.example.mapmemories.Lenta;

import android.content.Intent;
import android.graphics.drawable.Animatable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.badge.BadgeDrawable;
import com.example.mapmemories.Chats.ChatMessage; // Убедись, что импорт правильный для твоего проекта
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.core.content.ContextCompat;
import com.example.mapmemories.systemHelpers.MessageListenerService;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Chats.ChatListFragment;
import com.example.mapmemories.LogRegStart.LoginActivity;
import com.example.mapmemories.Post.CreatePostActivity;
import com.example.mapmemories.Profile.Profile;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.systemHelpers.DialogHelper;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TabLayout tabLayout;
    private MaterialCardView fabAdd, bottomDock;
    private ImageView profileButton, fabSettings, offlineBadge, fabAddIcon;

    private FirebaseAuth mAuth;
    private DatabaseReference userRef;
    private long backPressedTime;
    private boolean isDockVisible = true;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, Context.MODE_PRIVATE);
                    prefs.edit().putBoolean("notifications_enabled", isGranted).apply();

                    if (isGranted) {
                        startService(new Intent(this, MessageListenerService.class));
                        Toast.makeText(this, "Уведомления включены", Toast.LENGTH_SHORT).show();
                    }
                }
        );

        // ИСПРАВЛЕНИЕ БАГА СО СТАТУС-БАРОМ
        View rootLayout = findViewById(R.id.topHeader); // Указываем на верхний элемент
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(0, insets.top, 0, 0); // Добавляем отступ сверху равный высоте статус-бара
            return WindowInsetsCompat.CONSUMED;
        });

        mAuth = FirebaseAuth.getInstance();
        checkCurrentUser();

        initViews();
        setupViewPager();
        observeUnreadMessages();
        setupClickListeners();
        loadUserAvatar();
        observeOfflinePosts();
        setupDoubleBackExit();
        checkNotificationPermission();
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

    private void checkNotificationPermission() {
        SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, Context.MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                // Разрешение уже есть
                prefs.edit().putBoolean("notifications_enabled", true).apply();
                startService(new Intent(this, MessageListenerService.class));
            } else if (shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Пользователь ранее отказал, но не нажал "Больше не спрашивать"
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else {
                // Запрашиваем в первый раз
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        } else {
            // Для Android ниже 13 разрешение дается при установке
            prefs.edit().putBoolean("notifications_enabled", true).apply();
            startService(new Intent(this, MessageListenerService.class));
        }
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);
        tabLayout = findViewById(R.id.tabLayout);
        bottomDock = findViewById(R.id.bottomDock);
        fabAdd = findViewById(R.id.fabAdd);
        fabAddIcon = findViewById(R.id.fabAddIcon);
        fabSettings = findViewById(R.id.fabSettings);
        profileButton = findViewById(R.id.profileButton);
        offlineBadge = findViewById(R.id.offlineBadge);
    }

    public void toggleBottomDock(boolean show) {
        if (show && !isDockVisible) {
            bottomDock.animate().translationY(0).setDuration(300).withStartAction(() -> isDockVisible = true).start();
        } else if (!show && isDockVisible) {
            bottomDock.animate().translationY(bottomDock.getHeight() + 150).setDuration(300).withEndAction(() -> isDockVisible = false).start();
        }
    }

    private void observeUnreadMessages() {
        if (mAuth.getCurrentUser() == null) return;

        String currentUserId = mAuth.getCurrentUser().getUid();
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("chats");

        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                int totalUnreadCount = 0;

                // Проходимся по всем чатам
                for (DataSnapshot chatSnap : snapshot.getChildren()) {
                    String chatId = chatSnap.getKey();

                    // Если текущий пользователь есть в этом чате
                    if (chatId != null && chatId.contains(currentUserId)) {
                        DataSnapshot messagesSnap = chatSnap.child("messages");

                        // Считаем непрочитанные сообщения
                        for (DataSnapshot msgSnap : messagesSnap.getChildren()) {
                            ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                            if (msg != null &&
                                    msg.getReceiverId() != null &&
                                    msg.getReceiverId().equals(currentUserId) &&
                                    !msg.isRead()) {

                                // Проверка, не удалено ли сообщение (как у тебя в ChatListFragment)
                                if (msg.getDeletedBy() == null || !msg.getDeletedBy().equals(currentUserId)) {
                                    totalUnreadCount++;
                                }
                            }
                        }
                    }
                }

                // Обновляем UI бейджа
                updateChatsTabBadge(totalUnreadCount);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateChatsTabBadge(int unreadCount) {
        // Вкладка "Чаты" имеет индекс 1 (так как 0 это "Лента")
        TabLayout.Tab chatTab = tabLayout.getTabAt(1);

        if (chatTab != null) {
            if (unreadCount > 0) {
                BadgeDrawable badge = chatTab.getOrCreateBadge();
                badge.setNumber(unreadCount);
                // Красим бейдж в акцентный цвет (возьмет @color/accent из твоих ресурсов)
                badge.setBackgroundColor(ContextCompat.getColor(this, R.color.accent));
                // Цвет текста внутри кружочка (сделаем белый или цвет фона для контраста)
                badge.setBadgeTextColor(ContextCompat.getColor(this, R.color.primary));
                badge.setVisible(true);
            } else {
                // Если непрочитанных нет — удаляем кружочек
                chatTab.removeBadge();
            }
        }
    }

    private void setupViewPager() {
        MainPagerAdapter adapter = new MainPagerAdapter(this);
        viewPager.setAdapter(adapter);

        new TabLayoutMediator(tabLayout, viewPager, (tab, position) -> {
            tab.setText(position == 0 ? "Лента" : "Чаты");
        }).attach();
    }

    private void setupClickListeners() {
        profileButton.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(Profile.class, v);
        });

        fabSettings.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivityWithAnimation(Setting.class, v);
        });

        fabAdd.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            if (fabAddIcon.getDrawable() instanceof Animatable) {
                ((Animatable) fabAddIcon.getDrawable()).start();
            }
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                startActivityWithAnimation(CreatePostActivity.class, bottomDock);
            }, 250);
        });

        offlineBadge.setOnClickListener(v -> DialogHelper.showOfflineQueue(this, null));
    }

    private void loadUserAvatar() {
        if (userRef == null) return;
        userRef.child("profileImageUrl").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String url = snapshot.getValue(String.class);
                if (url != null && !isDestroyed()) Glide.with(MainActivity.this).load(url).circleCrop().into(profileButton);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void observeOfflinePosts() {
        AppDatabase.getDatabase(this).offlinePostDao().getAllPostsLive().observe(this, posts -> {
            offlineBadge.setVisibility(posts != null && !posts.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void setupDoubleBackExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    finishAffinity();
                } else {
                    Toast.makeText(MainActivity.this, "Нажмите еще раз для выхода", Toast.LENGTH_SHORT).show();
                    backPressedTime = System.currentTimeMillis();
                }
            }
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

    private static class MainPagerAdapter extends FragmentStateAdapter {
        public MainPagerAdapter(@NonNull FragmentActivity fragmentActivity) { super(fragmentActivity); }
        @NonNull @Override public Fragment createFragment(int position) {
            return position == 0 ? new LentaFragment() : new ChatListFragment();
        }
        @Override public int getItemCount() { return 2; }
    }

    @Override protected void onResume() { super.onResume(); updateStatus("online"); }
    @Override protected void onPause() { super.onPause(); updateStatus(System.currentTimeMillis()); }
    private void updateStatus(Object status) { if (userRef != null) userRef.child("status").setValue(status); }
}
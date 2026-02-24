package com.example.mapmemories;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private ChatListAdapter adapter;
    private List<User> chatUsers;
    private TextView emptyText;
    private String currentUserId;
    private SwipeBackHelper swipeBackHelper;
    private LinearLayout mainContentLayout;

    private static final int NOTIFICATION_PERMISSION_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> Close());

        recyclerView = findViewById(R.id.chatsRecyclerView);
        emptyText = findViewById(R.id.emptyChatsText);
        mainContentLayout = findViewById(R.id.mainContentLayout);
        swipeBackHelper = new SwipeBackHelper(this);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        chatUsers = new ArrayList<>();

        adapter = new ChatListAdapter(this, chatUsers, user -> {
            Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
            intent.putExtra("targetUserId", user.getId());
            startActivity(intent);
        });
        recyclerView.setAdapter(adapter);

        loadChats();

        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            mainContentLayout.setVisibility(View.INVISIBLE);
            ViewTreeObserver viewTreeObserver = mainContentLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainContentLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealActivity(getIntent().getIntExtra("revealX", 0),
                                getIntent().getIntExtra("revealY", 0));
                    }
                });
            }
        }

        // ЗАПРАШИВАЕМ РАЗРЕШЕНИЕ И ЗАПУСКАЕМ СЕРВИС
        checkNotificationPermissionAndStartService();
    }

    private void checkNotificationPermissionAndStartService() {
        // Для Android 13 и выше нужно спросить разрешение у пользователя
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            } else {
                startBackgroundService();
            }
        } else {
            // Для старых Android разрешение не нужно, просто запускаем
            startBackgroundService();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startBackgroundService();
            }
        }
    }

    private void startBackgroundService() {
        Intent serviceIntent = new Intent(this, MessageListenerService.class);
        startService(serviceIntent);
    }

    private void loadChats() {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("chats");

        chatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                chatUsers.clear();
                List<String> targetUserIds = new ArrayList<>();

                for (DataSnapshot chatSnapshot : snapshot.getChildren()) {
                    String chatKey = chatSnapshot.getKey();

                    if (chatKey != null && chatKey.contains(currentUserId)) {
                        String targetId = chatKey.replace(currentUserId, "").replace("_", "");
                        if (!targetId.isEmpty()) {
                            targetUserIds.add(targetId);
                        }
                    }
                }

                if (targetUserIds.isEmpty()) {
                    emptyText.setVisibility(View.VISIBLE);
                } else {
                    emptyText.setVisibility(View.GONE);
                    for (String id : targetUserIds) {
                        loadUserProfile(id);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUserProfile(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    user.setId(snapshot.getKey());
                    chatUsers.add(user);
                    adapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void revealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(mainContentLayout.getWidth(), mainContentLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(mainContentLayout, x, y, 0, finalRadius);
        circularReveal.setDuration(400);
        circularReveal.setInterpolator(new AccelerateInterpolator());
        mainContentLayout.setVisibility(View.VISIBLE);
        circularReveal.start();
    }

    private void unRevealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(mainContentLayout.getWidth(), mainContentLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(mainContentLayout, x, y, finalRadius, 0);
        circularReveal.setDuration(500);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mainContentLayout.setVisibility(View.INVISIBLE);
                finish();
                overridePendingTransition(0, 0);
            }
        });
        circularReveal.start();
    }

    public void Close() {
        int revealX = getIntent().getIntExtra("revealX", 0);
        int revealY = getIntent().getIntExtra("revealY", 0);
        unRevealActivity(revealX, revealY);
    }

    @Override
    public void onBackPressed() {Close();}

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) {
            return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        }
        return super.dispatchTouchEvent(ev);
    }
}
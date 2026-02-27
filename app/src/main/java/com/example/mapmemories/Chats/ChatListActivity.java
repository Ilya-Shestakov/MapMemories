package com.example.mapmemories.Chats;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mapmemories.systemHelpers.MessageListenerService;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.SwipeBackHelper;
import com.example.mapmemories.Profile.User;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class ChatListActivity extends AppCompatActivity {

    private RecyclerView chatsRecyclerView, globalRecyclerView;
    private ChatListAdapter localAdapter, globalAdapter;

    private List<User> allLocalUsers;      // Все текущие чаты
    private List<User> filteredLocalUsers; // Отфильтрованные чаты
    private List<User> globalUsers;        // Результаты глобального поиска

    private TextView emptyText, localChatsTitle;
    private LinearLayout mainContentLayout, globalSearchContainer;
    private EditText searchInput;

    private String currentUserId;
    private SwipeBackHelper swipeBackHelper;
    private static final int NOTIFICATION_PERMISSION_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat_list);

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        swipeBackHelper = new SwipeBackHelper(this);

        initViews();
        setupRecyclerViews();
        setupSearch();
        loadChats();
        handleRevealAnimation(savedInstanceState);
        checkNotificationPermissionAndStartService();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> Close());

        mainContentLayout = findViewById(R.id.mainContentLayout);
        chatsRecyclerView = findViewById(R.id.chatsRecyclerView);
        globalRecyclerView = findViewById(R.id.globalRecyclerView);
        emptyText = findViewById(R.id.emptyChatsText);
        searchInput = findViewById(R.id.searchInput);
        globalSearchContainer = findViewById(R.id.globalSearchContainer);
        localChatsTitle = findViewById(R.id.localChatsTitle);
    }

    private void setupRecyclerViews() {
        allLocalUsers = new ArrayList<>();
        filteredLocalUsers = new ArrayList<>();
        globalUsers = new ArrayList<>();

        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        globalRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Адаптер для локальных чатов
        localAdapter = new ChatListAdapter(this, filteredLocalUsers, user -> openChat(user.getId()));
        chatsRecyclerView.setAdapter(localAdapter);

        // Адаптер для глобального поиска
        globalAdapter = new ChatListAdapter(this, globalUsers, user -> openChat(user.getId()));
        globalRecyclerView.setAdapter(globalAdapter);
    }

    private void openChat(String targetUserId) {
        Intent intent = new Intent(ChatListActivity.this, ChatActivity.class);
        intent.putExtra("targetUserId", targetUserId);
        startActivity(intent);
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                performSearch(s.toString().trim());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });
    }

    private void performSearch(String query) {
        filteredLocalUsers.clear();

        if (query.isEmpty()) {
            // Если поиск пуст - скрываем глобальный блок, показываем все свои чаты
            globalSearchContainer.setVisibility(View.GONE);
            localChatsTitle.setVisibility(View.GONE);
            filteredLocalUsers.addAll(allLocalUsers);
            localAdapter.notifyDataSetChanged();
            updateEmptyState();
            return;
        }

        // 1. Локальный поиск (по своим чатам)
        for (User user : allLocalUsers) {
            if (user.getUsername() != null && user.getUsername().toLowerCase().contains(query.toLowerCase())) {
                filteredLocalUsers.add(user);
            }
        }
        localAdapter.notifyDataSetChanged();
        localChatsTitle.setVisibility(filteredLocalUsers.isEmpty() ? View.GONE : View.VISIBLE);
        updateEmptyState();

        // 2. Глобальный поиск (по БД)
        searchGlobalUsers(query);
    }

    private void searchGlobalUsers(String query) {
        DatabaseReference usersRef = FirebaseDatabase.getInstance().getReference("users");
        // Ищем совпадения по нику. Ограничиваем до 3 результатов.
        Query searchQuery = usersRef.orderByChild("username").startAt(query).endAt(query + "\uf8ff").limitToFirst(3);

        searchQuery.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                globalUsers.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    if (user != null) {
                        user.setId(ds.getKey());
                        // Исключаем себя и тех, кто уже есть в локальных чатах
                        if (!user.getId().equals(currentUserId) && !isUserInLocalChats(user.getId())) {
                            globalUsers.add(user);
                        }
                    }
                }

                globalAdapter.notifyDataSetChanged();
                globalSearchContainer.setVisibility(globalUsers.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private boolean isUserInLocalChats(String userId) {
        for (User u : allLocalUsers) {
            if (u.getId().equals(userId)) return true;
        }
        return false;
    }

    private void loadChats() {
        DatabaseReference chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        chatsRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allLocalUsers.clear();
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
                    updateEmptyState();
                } else {
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
                    allLocalUsers.add(user);

                    // Если строка поиска пуста, сразу добавляем в отображаемый список
                    if (searchInput.getText().toString().trim().isEmpty()) {
                        filteredLocalUsers.add(user);
                        localAdapter.notifyDataSetChanged();
                        updateEmptyState();
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateEmptyState() {
        if (filteredLocalUsers.isEmpty() && searchInput.getText().toString().trim().isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("У вас пока нет диалогов");
        } else if (filteredLocalUsers.isEmpty() && globalUsers.isEmpty()) {
            emptyText.setVisibility(View.VISIBLE);
            emptyText.setText("Ничего не найдено");
        } else {
            emptyText.setVisibility(View.GONE);
        }
    }

    // --- Анимации и системные методы ---

    private void handleRevealAnimation(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            mainContentLayout.setVisibility(View.INVISIBLE);
            ViewTreeObserver viewTreeObserver = mainContentLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainContentLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealActivity(getIntent().getIntExtra("revealX", 0), getIntent().getIntExtra("revealY", 0));
                    }
                });
            }
        }
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
    public void onBackPressed() { Close(); }


    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) {
            return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        }
        return super.dispatchTouchEvent(ev);
    }

    private void checkNotificationPermissionAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
            } else {
                startService(new Intent(this, MessageListenerService.class));
            }
        } else {
            startService(new Intent(this, MessageListenerService.class));
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startService(new Intent(this, MessageListenerService.class));
        }
    }
}
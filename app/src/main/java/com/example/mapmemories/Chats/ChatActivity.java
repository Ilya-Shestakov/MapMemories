package com.example.mapmemories.Chats;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Profile.PickPostActivity;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChatActivity extends AppCompatActivity {

    public static String currentChatUserId = null;

    private String targetUserId;
    private String currentUserId;
    private String chatId;
    private String editingMessageId = null;

    private LinearLayout userInfoContainer;
    private ImageView ivChatAvatar;
    private TextView tvChatUsername, tvChatStatus;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private ImageButton btnSendPost, btnSendText;
    private EditText etMessageInput;

    private DatabaseReference chatRef;
    private DatabaseReference targetUserRef;
    private DatabaseReference myStatusRef;
    private ValueEventListener statusListener;
    private ActivityResultLauncher<Intent> pickPostLauncher;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences preferences = newBase.getSharedPreferences(Setting.PREFS_NAME, Context.MODE_PRIVATE);
        float scale = preferences.getFloat("text_scale", 1.0f);
        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scale;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        // ФИКС КЛАВИАТУРЫ ДЛЯ ANDROID 15/16 (Samsung S23 Ultra и др.)
        View rootLayout = findViewById(R.id.rootLayout);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        targetUserId = getIntent().getStringExtra("targetUserId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (TextUtils.isEmpty(targetUserId)) {
            finish();
            return;
        }

        chatId = currentUserId.compareTo(targetUserId) < 0 ?
                currentUserId + "_" + targetUserId : targetUserId + "_" + currentUserId;

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages");
        targetUserRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId);
        myStatusRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("status");

        initViews();
        setupPostPicker();
        loadTargetUserData();
        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentChatUserId = targetUserId;
        updateMyStatus("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatUserId = null;
        updateMyStatus(System.currentTimeMillis());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (targetUserRef != null && statusListener != null) {
            targetUserRef.removeEventListener(statusListener);
        }
    }

    private void updateMyStatus(Object status) {
        SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, MODE_PRIVATE);
        boolean hideOnline = prefs.getBoolean("privacy_hide_online", false);

        if (hideOnline) {
            myStatusRef.setValue("hidden");
        } else {
            myStatusRef.setValue(status);
            myStatusRef.onDisconnect().setValue(System.currentTimeMillis());
        }
    }

    private void initViews() {
        userInfoContainer = findViewById(R.id.userInfoContainer);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        tvChatUsername = findViewById(R.id.tvChatUsername);
        tvChatStatus = findViewById(R.id.tvChatStatus);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        userInfoContainer.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Intent intent = new Intent(ChatActivity.this, UserProfileActivity.class);
            intent.putExtra("targetUserId", targetUserId);
            startActivity(intent);
        });

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        btnSendPost = findViewById(R.id.btnSendPost);
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSendText = findViewById(R.id.btnSendText);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList, new ChatAdapter.ChatActionListener() {
            @Override
            public void onEditMessage(ChatMessage message) {
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(message.getText().length());
                editingMessageId = message.getMessageId();
            }

            @Override
            public void onDeleteMessage(ChatMessage message, boolean forEveryone) {
                if (forEveryone) {
                    chatRef.child(message.getMessageId()).removeValue();
                } else {
                    if (message.getDeletedBy() != null && !message.getDeletedBy().equals(currentUserId)) {
                        chatRef.child(message.getMessageId()).removeValue();
                    } else {
                        chatRef.child(message.getMessageId()).child("deletedBy").setValue(currentUserId);
                    }
                }
            }
        });
        chatRecyclerView.setAdapter(chatAdapter);

        // Скролл вниз при открытии клавиатуры
        chatRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && !messageList.isEmpty()) {
                chatRecyclerView.postDelayed(() -> chatRecyclerView.smoothScrollToPosition(messageList.size() - 1), 100);
            }
        });

        btnSendPost.setOnClickListener(v -> pickPostLauncher.launch(new Intent(ChatActivity.this, PickPostActivity.class)));

        btnSendText.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                etMessageInput.setText(""); // Очищаем сразу для отзывчивости интерфейса
                if (editingMessageId != null) {
                    chatRef.child(editingMessageId).child("text").setValue(text);
                    editingMessageId = null;
                } else {
                    sendTextMessage(text);
                }
            }
        });
    }

    private void loadTargetUserData() {
        statusListener = targetUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isDestroyed()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String avatarUrl = snapshot.child("profileImageUrl").getValue(String.class);
                    Object statusObj = snapshot.child("status").getValue();

                    boolean isHidden = snapshot.child("privacy").child("hide_online").exists() &&
                            Boolean.TRUE.equals(snapshot.child("privacy").child("hide_online").getValue(Boolean.class));

                    tvChatUsername.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(ChatActivity.this).load(avatarUrl).circleCrop()
                                .placeholder(R.drawable.ic_profile_placeholder).into(ivChatAvatar);
                    }

                    String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                    tvChatStatus.setText(statusText);

                    if ("в сети".equals(statusText)) {
                        tvChatStatus.setTextColor(getResources().getColor(R.color.online_indicator));
                    } else {
                        tvChatStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadMessages() {
        chatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messageList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ChatMessage msg = ds.getValue(ChatMessage.class);
                    if (msg != null && (msg.getDeletedBy() == null || !msg.getDeletedBy().equals(currentUserId))) {
                        messageList.add(msg);
                    }
                }
                chatAdapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void sendTextMessage(String text) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, text, System.currentTimeMillis(), "text");
            message.setMessageId(messageId);
            chatRef.child(messageId).setValue(message);
            sendNotificationTrigger(text);
        }
    }

    private void setupPostPicker() {
        pickPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String selectedPostId = result.getData().getStringExtra("selectedPostId");
                        if (selectedPostId != null) sendMessage(selectedPostId);
                    }
                }
        );
    }

    private void sendMessage(String postId) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, postId, System.currentTimeMillis());
            message.setMessageId(messageId);
            chatRef.child(messageId).setValue(message);
            sendNotificationTrigger("Отправил(а) воспоминание 🗺️");
        }
    }

    private void sendNotificationTrigger(String text) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(targetUserId).push();
        HashMap<String, String> notifData = new HashMap<>();
        notifData.put("senderId", currentUserId);
        notifData.put("text", text);
        notifData.put("type", "chat");
        notifRef.setValue(notifData);
    }
}
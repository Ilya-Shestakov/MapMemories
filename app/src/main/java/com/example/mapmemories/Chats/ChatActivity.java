package com.example.mapmemories.Chats;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Profile.PickPostActivity;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.R;
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
    private TextView tvChatUsername;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private ImageButton btnSendPost;

    private EditText etMessageInput;
    private ImageButton btnSendText;

    private DatabaseReference chatRef;
    private ActivityResultLauncher<Intent> pickPostLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);

        targetUserId = getIntent().getStringExtra("targetUserId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (TextUtils.isEmpty(targetUserId)) {
            finish();
            return;
        }

        if (currentUserId.compareTo(targetUserId) < 0) {
            chatId = currentUserId + "_" + targetUserId;
        } else {
            chatId = targetUserId + "_" + currentUserId;
        }

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages");

        initViews();
        setupPostPicker();
        loadTargetUserData();
        loadMessages();
    }

    @Override
    protected void onResume() {
        super.onResume();
        currentChatUserId = targetUserId;
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatUserId = null;
    }

    private void initViews() {
        userInfoContainer = findViewById(R.id.userInfoContainer);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        tvChatUsername = findViewById(R.id.tvChatUsername);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Переход в профиль по клику на шапку
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
                Toast.makeText(ChatActivity.this, "Редактирование сообщения", Toast.LENGTH_SHORT).show();
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

        btnSendPost.setOnClickListener(v -> {
            Intent intent = new Intent(ChatActivity.this, PickPostActivity.class);
            pickPostLauncher.launch(intent);
        });

        btnSendText.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                if (editingMessageId != null) {
                    chatRef.child(editingMessageId).child("text").setValue(text);
                    editingMessageId = null;
                } else {
                    sendTextMessage(text);
                }
                etMessageInput.setText("");
            }
        });
    }

    private void loadTargetUserData() {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String avatarUrl = snapshot.child("profileImageUrl").getValue(String.class);

                    if (username != null && !username.isEmpty()) {
                        tvChatUsername.setText(username);
                    } else {
                        tvChatUsername.setText("Пользователь");
                    }

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(ChatActivity.this)
                                .load(avatarUrl)
                                .circleCrop()
                                .placeholder(R.drawable.ic_launcher_background)
                                .into(ivChatAvatar);
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
                    if (msg != null) {
                        if (msg.getDeletedBy() == null || !msg.getDeletedBy().equals(currentUserId)) {
                            messageList.add(msg);
                        }
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
        long timestamp = System.currentTimeMillis();

        ChatMessage message = new ChatMessage(currentUserId, targetUserId, text, timestamp, "text");
        message.setMessageId(messageId);

        if (messageId != null) {
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
                        if (selectedPostId != null) {
                            sendMessage(selectedPostId);
                        }
                    }
                }
        );
    }

    private void sendMessage(String postId) {
        String messageId = chatRef.push().getKey();
        long timestamp = System.currentTimeMillis();

        ChatMessage message = new ChatMessage(currentUserId, targetUserId, postId, timestamp);
        message.setMessageId(messageId);

        if (messageId != null) {
            chatRef.child(messageId).setValue(message);
            sendNotificationTrigger("Отправил(а) воспоминание 🗺️");
        }
    }

    private void sendNotificationTrigger(String text) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance()
                .getReference("notifications")
                .child(targetUserId)
                .push();

        HashMap<String, String> notifData = new HashMap<>();
        notifData.put("senderId", currentUserId);
        notifData.put("text", text);
        notifData.put("type", "chat");

        notifRef.setValue(notifData);
    }
}
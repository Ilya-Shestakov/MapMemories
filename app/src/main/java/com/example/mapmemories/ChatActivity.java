package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
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

public class ChatActivity extends AppCompatActivity {

    private String targetUserId;
    private String currentUserId;
    private String chatId;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private ImageButton btnSendPost; // Кнопка "Скрепка" или "Плюс"

    private DatabaseReference chatRef;
    private ActivityResultLauncher<Intent> pickPostLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat); // См. XML ниже

        targetUserId = getIntent().getStringExtra("targetUserId");
        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (TextUtils.isEmpty(targetUserId)) {
            finish();
            return;
        }

        // Генерируем ID чата: всегда "меньшийID_большийID"
        if (currentUserId.compareTo(targetUserId) < 0) {
            chatId = currentUserId + "_" + targetUserId;
        } else {
            chatId = targetUserId + "_" + currentUserId;
        }

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages");

        initViews();
        setupPostPicker();
        loadMessages();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle("Чат");
        toolbar.setNavigationOnClickListener(v -> finish());

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        btnSendPost = findViewById(R.id.btnSendPost);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Сообщения снизу
        chatRecyclerView.setLayoutManager(layoutManager);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList);
        chatRecyclerView.setAdapter(chatAdapter);

        // Кнопка отправки поста
        btnSendPost.setOnClickListener(v -> {
            Intent intent = new Intent(ChatActivity.this, PickPostActivity.class);
            pickPostLauncher.launch(intent);
        });
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
            // Можно добавить прокрутку вниз
        }
    }

    private void loadMessages() {
        chatRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                messageList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    ChatMessage msg = ds.getValue(ChatMessage.class);
                    if (msg != null) {
                        messageList.add(msg);
                    }
                }
                chatAdapter.notifyDataSetChanged();
                if (!messageList.isEmpty()) {
                    chatRecyclerView.smoothScrollToPosition(messageList.size() - 1);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
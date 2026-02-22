package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.EditText;
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

    private String editingMessageId = null;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private ImageButton btnSendPost; // Кнопка "Скрепка" или "Плюс"

    private EditText etMessageInput;
    private ImageButton btnSendText;

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
        etMessageInput = findViewById(R.id.etMessageInput);
        btnSendText = findViewById(R.id.btnSendText);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true); // Сообщения снизу
        chatRecyclerView.setLayoutManager(layoutManager);

        messageList = new ArrayList<>();

        // ИНИЦИАЛИЗИРУЕМ АДАПТЕР ОДИН РАЗ (С 3 ПАРАМЕТРАМИ)
        chatAdapter = new ChatAdapter(this, messageList, new ChatAdapter.ChatActionListener() {
            @Override
            public void onEditMessage(ChatMessage message) {
                // Переносим текст в поле ввода
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(message.getText().length()); // Курсор в конец
                editingMessageId = message.getMessageId(); // Запоминаем, что мы редактируем
                Toast.makeText(ChatActivity.this, "Редактирование сообщения", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteMessage(ChatMessage message, boolean forEveryone) {
                if (forEveryone) {
                    // Удаляем из базы полностью
                    chatRef.child(message.getMessageId()).removeValue();
                } else {
                    // Удаляем только для себя
                    if (message.getDeletedBy() != null && !message.getDeletedBy().equals(currentUserId)) {
                        // Если собеседник уже удалил у себя, а теперь удаляем мы -> удаляем из базы совсем
                        chatRef.child(message.getMessageId()).removeValue();
                    } else {
                        // Помечаем, что мы скрыли это сообщение
                        chatRef.child(message.getMessageId()).child("deletedBy").setValue(currentUserId);
                    }
                }
            }
        });
        chatRecyclerView.setAdapter(chatAdapter);

        // Кнопка отправки поста
        btnSendPost.setOnClickListener(v -> {
            Intent intent = new Intent(ChatActivity.this, PickPostActivity.class);
            pickPostLauncher.launch(intent);
        });

        // Кнопка отправки текста (ОДИН РАЗ)
        btnSendText.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (!text.isEmpty()) {
                if (editingMessageId != null) {
                    // Если мы в режиме редактирования - обновляем текст
                    chatRef.child(editingMessageId).child("text").setValue(text);
                    editingMessageId = null; // Выходим из режима редактирования
                } else {
                    // Иначе отправляем новое
                    sendTextMessage(text);
                }
                etMessageInput.setText("");
            }
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
                        // Проверяем, не удалил ли текущий юзер это сообщение для себя
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

        // Используем конструктор для поста (он сам ставит type = "post")
        ChatMessage message = new ChatMessage(currentUserId, targetUserId, postId, timestamp);
        message.setMessageId(messageId);

        if (messageId != null) {
            chatRef.child(messageId).setValue(message);
        }
    }
}
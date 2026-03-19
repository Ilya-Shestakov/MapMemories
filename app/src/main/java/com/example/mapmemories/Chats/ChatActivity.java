package com.example.mapmemories.Chats;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.example.mapmemories.Profile.PickPostActivity;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    public static String currentChatUserId = null;

    private float startX;
    private float startY;

    private String targetUserId;
    private String currentUserId;
    private String chatId;
    private String editingMessageId = null;
    private Uri selectedImageUri = null;

    private LinearLayout userInfoContainer;
    private ImageView ivChatAvatar;
    private TextView tvChatUsername, tvChatStatus;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;

    private ImageButton btnAttach, btnSend, btnRemoveImage;
    private EditText etMessageInput;
    private ConstraintLayout imagePreviewContainer;
    private ImageView ivPreviewImage;

    private DatabaseReference chatRef;
    private DatabaseReference targetUserRef;
    private DatabaseReference myStatusRef;
    private ValueEventListener statusListener;
    private ChildEventListener messagesListener;

    private ActivityResultLauncher<Intent> pickPostLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    private Cloudinary cloudinary;
    private ProgressDialog progressDialog;

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

        initCloudinary();
        initViews();
        setupLaunchers();
        loadTargetUserData();
        loadMessagesOptimized();
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU"); // ВНИМАНИЕ: СМЕНИ КЛЮЧИ В КОНСОЛИ!
        cloudinary = new Cloudinary(config);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Отправка фото...");
        progressDialog.setCancelable(false);
    }

    private void initViews() {
        userInfoContainer = findViewById(R.id.userInfoContainer);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        tvChatUsername = findViewById(R.id.tvChatUsername);
        tvChatStatus = findViewById(R.id.tvChatStatus);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setNavigationOnClickListener(v -> finish());

        userInfoContainer.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Intent intent = new Intent(ChatActivity.this, UserProfileActivity.class);
            intent.putExtra("targetUserId", targetUserId);
            startActivity(intent);
        });

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        btnAttach = findViewById(R.id.btnAttach);
        btnSend = findViewById(R.id.btnSend);
        etMessageInput = findViewById(R.id.etMessageInput);

        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        ivPreviewImage = findViewById(R.id.ivPreviewImage);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);

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
                updateInputUI();
            }

            @Override
            public void onDeleteMessage(ChatMessage message, boolean forEveryone) {
                if (forEveryone) {
                    chatRef.child(message.getMessageId()).removeValue();
                } else {
                    chatRef.child(message.getMessageId()).child("deletedBy").setValue(currentUserId);
                }
            }
        });
        chatRecyclerView.setAdapter(chatAdapter);

        chatRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && !messageList.isEmpty()) {
                chatRecyclerView.postDelayed(() -> chatRecyclerView.smoothScrollToPosition(messageList.size() - 1), 100);
            }
        });

        // ЛОГИКА СКРЫТИЯ СКРЕПКИ ПРИ ВВОДЕ
        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateInputUI();
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(v -> showAttachmentMenu());

        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            imagePreviewContainer.setVisibility(View.GONE);
            updateInputUI();
        });

        btnSend.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();

            if (selectedImageUri != null) {
                uploadImageToCloudinaryAndSend(selectedImageUri, text);
                selectedImageUri = null;
                imagePreviewContainer.setVisibility(View.GONE);
                etMessageInput.setText("");
                updateInputUI();
            } else if (!text.isEmpty()) {
                etMessageInput.setText("");
                if (editingMessageId != null) {
                    chatRef.child(editingMessageId).child("text").setValue(text);
                    editingMessageId = null;
                } else {
                    sendTextMessage(text);
                }
                updateInputUI();
            }
        });
    }

    private void updateInputUI() {
        boolean hasText = etMessageInput.getText().toString().trim().length() > 0;
        boolean hasImage = selectedImageUri != null;

        if (hasText || hasImage) {
            btnAttach.setVisibility(View.GONE);
            btnSend.setVisibility(View.VISIBLE);
        } else {
            btnAttach.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.GONE);
        }
    }

    private void showAttachmentMenu() {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(this);
        View view = LayoutInflater.from(this).inflate(R.layout.bottom_sheet_attach, null);
        bottomSheetDialog.setContentView(view);
        ((View) view.getParent()).setBackgroundColor(android.graphics.Color.TRANSPARENT);

        LinearLayout btnAttachGallery = view.findViewById(R.id.btnAttachGallery);
        LinearLayout btnAttachPost = view.findViewById(R.id.btnAttachPost);

        btnAttachGallery.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            pickImageLauncher.launch("image/*");
        });

        btnAttachPost.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            pickPostLauncher.launch(new Intent(ChatActivity.this, PickPostActivity.class));
        });

        bottomSheetDialog.show();
    }

    private void setupLaunchers() {
        pickPostLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        String selectedPostId = result.getData().getStringExtra("selectedPostId");
                        if (selectedPostId != null) sendPostMessage(selectedPostId);
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        selectedImageUri = uri;
                        imagePreviewContainer.setVisibility(View.VISIBLE);
                        Glide.with(this).load(uri).into(ivPreviewImage);
                        updateInputUI();
                        etMessageInput.requestFocus();
                    }
                }
        );
    }

    // ЗАГРУЗКА В CLOUDINARY
    private void uploadImageToCloudinaryAndSend(Uri imageUri, String caption) {
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(imageUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String imageUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (!isFinishing() && !isDestroyed()) {
                        sendImageMessage(imageUrl, caption);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(ChatActivity.this, "Ошибка отправки фото", Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void sendImageMessage(String imageUrl, String caption) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, imageUrl, caption, System.currentTimeMillis(), "image");
            message.setMessageId(messageId);
            chatRef.child(messageId).setValue(message);
            sendNotificationTrigger(caption.isEmpty() ? "Отправил(а) фото 📷" : "📷 " + caption);
        }
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

    private void sendPostMessage(String postId) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, postId, System.currentTimeMillis());
            message.setMessageId(messageId);
            chatRef.child(messageId).setValue(message);
            sendNotificationTrigger("Отправил(а) воспоминание 🗺️");
        }
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
                        Glide.with(ChatActivity.this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(ivChatAvatar);
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
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadMessagesOptimized() {
        messagesListener = chatRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage msg = snapshot.getValue(ChatMessage.class);
                if (msg != null && (msg.getDeletedBy() == null || !msg.getDeletedBy().equals(currentUserId))) {

                    // --- ЛОГИКА: МЫ ПРОЧИТАЛИ ВХОДЯЩЕЕ СООБЩЕНИЕ ---
                    // Сохраняем "read", чтобы оно соответствовало новой модели БД
                    if (msg.getReceiverId().equals(currentUserId) && !msg.isRead()) {
                        snapshot.getRef().child("read").setValue(true);
                        msg.setRead(true);
                    }

                    messageList.add(msg);
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                }
            }
            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage updatedMsg = snapshot.getValue(ChatMessage.class);
                if (updatedMsg != null) {
                    for (int i = 0; i < messageList.size(); i++) {
                        if (messageList.get(i).getMessageId().equals(updatedMsg.getMessageId())) {
                            if (updatedMsg.getDeletedBy() != null && updatedMsg.getDeletedBy().equals(currentUserId)) {
                                messageList.remove(i);
                                chatAdapter.notifyItemRemoved(i);
                            } else {
                                messageList.set(i, updatedMsg);
                                chatAdapter.notifyItemChanged(i);
                            }
                            break;
                        }
                    }
                }
            }
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {
                String removedId = snapshot.getKey();
                for (int i = 0; i < messageList.size(); i++) {
                    if (messageList.get(i).getMessageId().equals(removedId)) {
                        messageList.remove(i);
                        chatAdapter.notifyItemRemoved(i);
                        break;
                    }
                }
            }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
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

    private void sendNotificationTrigger(String text) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifications").child(targetUserId).push();
        HashMap<String, String> notifData = new HashMap<>();
        notifData.put("senderId", currentUserId);
        notifData.put("text", text);
        notifData.put("type", "chat");
        notifRef.setValue(notifData);
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        switch (ev.getAction()) {
            case android.view.MotionEvent.ACTION_DOWN:
                startX = ev.getX();
                startY = ev.getY();
                break;
            case android.view.MotionEvent.ACTION_UP:
                float endX = ev.getX();
                float endY = ev.getY();

                // Получаем плотность пикселей экрана для независимости от устройства
                float density = getResources().getDisplayMetrics().density;
                float edgeArea = 40 * density; // Зона 40dp от левого края
                float minSwipeDist = 60 * density; // Минимальная длина свайпа 60dp

                // Если свайпнут от края направо и палец не ушел сильно вверх/вниз
                if (startX <= edgeArea && (endX - startX) >= minSwipeDist && Math.abs(endY - startY) < minSwipeDist) {
                    finish();
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
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
        if (targetUserRef != null && statusListener != null) targetUserRef.removeEventListener(statusListener);
        if (chatRef != null && messagesListener != null) chatRef.removeEventListener(messagesListener);
    }
}
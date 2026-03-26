package com.example.mapmemories.Chats;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

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
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
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
import com.google.android.material.floatingactionbutton.FloatingActionButton;
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

    private float startX, startY;

    private String targetUserId, currentUserId, chatId;
    private String editingMessageId = null;
    private Uri selectedImageUri = null;
    private ChatMessage replyingToMessage = null;

    private LinearLayout userInfoContainer, emptyChatContainer, pinnedMessageContainer;
    private ImageView ivChatAvatar;
    private TextView tvChatUsername, tvChatStatus, btnSayHello, tvPinnedText;
    private ImageButton btnUnpin;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private FloatingActionButton fabScrollDown;

    private ImageButton btnAttach, btnSend, btnRemoveImage;
    private EditText etMessageInput;
    private ConstraintLayout imagePreviewContainer, replyPreviewContainer;
    private ImageView ivPreviewImage;
    private TextView tvReplySender, tvReplyText;
    private ImageButton btnCloseReply;

    private DatabaseReference chatRef, targetUserRef, myStatusRef, pinnedRef;
    private ValueEventListener statusListener, pinnedListener;
    private ChildEventListener messagesListener;

    private ActivityResultLauncher<Intent> pickPostLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    private boolean isSwipingToClose = false;

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

        if (TextUtils.isEmpty(targetUserId)) { finish(); return; }

        chatId = currentUserId.compareTo(targetUserId) < 0 ? currentUserId + "_" + targetUserId : targetUserId + "_" + currentUserId;

        chatRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("messages");
        pinnedRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("pinnedMessageId");
        targetUserRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId);
        myStatusRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("status");

        initCloudinary();
        initViews();
        setupLaunchers();
        loadTargetUserData();
        loadMessagesOptimized();
        loadPinnedMessage();
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
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
        emptyChatContainer = findViewById(R.id.emptyChatContainer);
        btnSayHello = findViewById(R.id.btnSayHello);
        pinnedMessageContainer = findViewById(R.id.pinnedMessageContainer);
        tvPinnedText = findViewById(R.id.tvPinnedText);
        btnUnpin = findViewById(R.id.btnUnpin);

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
        chatRecyclerView.setItemAnimator(new DefaultItemAnimator()); // Плавные анимации
        fabScrollDown = findViewById(R.id.fabScrollDown);
        btnAttach = findViewById(R.id.btnAttach);
        btnSend = findViewById(R.id.btnSend);
        etMessageInput = findViewById(R.id.etMessageInput);

        imagePreviewContainer = findViewById(R.id.imagePreviewContainer);
        ivPreviewImage = findViewById(R.id.ivPreviewImage);
        btnRemoveImage = findViewById(R.id.btnRemoveImage);

        replyPreviewContainer = findViewById(R.id.replyPreviewContainer);
        tvReplySender = findViewById(R.id.tvReplySender);
        tvReplyText = findViewById(R.id.tvReplyText);
        btnCloseReply = findViewById(R.id.btnCloseReply);

        btnRemoveImage.setOnClickListener(v -> {
            selectedImageUri = null;
            imagePreviewContainer.setVisibility(View.GONE);
            updateInputUI();
        });

        btnCloseReply.setOnClickListener(v -> closeReplyPreview());

        btnSayHello.setOnClickListener(v -> sendTextMessage("👋 Привет!"));

        btnUnpin.setOnClickListener(v -> pinnedRef.removeValue());

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        chatRecyclerView.setLayoutManager(layoutManager);

        messageList = new ArrayList<>();
        chatAdapter = new ChatAdapter(this, messageList, new ChatAdapter.ChatActionListener() {
            @Override
            public void onEditMessage(ChatMessage message) {
                // 1. Сначала устанавливаем ID редактируемого сообщения
                editingMessageId = message.getMessageId();
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(message.getText().length());

                // 2. Показываем плашку "Редактирование" над полем ввода
                replyPreviewContainer.setVisibility(View.VISIBLE);
                tvReplySender.setText("Редактирование");
                tvReplyText.setText(message.getText());

                // 3. Сохраняем старую цитату в фоне (чтобы не затереть её при сохранении)
                if (message.getReplyMessageId() != null) {
                    replyingToMessage = new ChatMessage();
                    replyingToMessage.setMessageId(message.getReplyMessageId());
                    replyingToMessage.setSenderId(message.getReplySenderId());
                    replyingToMessage.setText(message.getReplyText());
                    replyingToMessage.setType("text");
                } else {
                    replyingToMessage = null;
                }

                updateInputUI();
                etMessageInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etMessageInput, InputMethodManager.SHOW_IMPLICIT);
            }

            @Override
            public void onDeleteMessage(ChatMessage message, boolean forEveryone) {
                // Удаляем само сообщение
                if (forEveryone) chatRef.child(message.getMessageId()).removeValue();
                else chatRef.child(message.getMessageId()).child("deletedBy").setValue(currentUserId);

                // ИСПРАВЛЕНИЕ: Проверяем, было ли оно в закрепе, и если да — удаляем закреп
                pinnedRef.addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists() && snapshot.getValue(String.class).equals(message.getMessageId())) {
                            pinnedRef.removeValue();
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
            }

            @Override
            public void onPinMessage(ChatMessage message) {
                pinnedRef.setValue(message.getMessageId());
            }

            @Override
            public void onMessageHighlighted(String messageId) {
                VibratorHelper.vibrate(ChatActivity.this, 20);
            }

            @Override
            public void onReplyMessage(ChatMessage message) {
                setupReplyPreview(message, false);
            }

            @Override
            public void onQuoteClicked(String messageId) {
                scrollToAndHighlightMessage(messageId);
            }
        });
        chatRecyclerView.setAdapter(chatAdapter);

        chatRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                if (!recyclerView.canScrollVertically(1)) fabScrollDown.setVisibility(View.GONE);
                else if (dy < 0) fabScrollDown.setVisibility(View.VISIBLE); // Показываем только если скроллим вверх
            }
        });

        fabScrollDown.setOnClickListener(v -> chatRecyclerView.smoothScrollToPosition(messageList.size() - 1));

        MessageSwipeController swipeController = new MessageSwipeController(this, position -> {
            ChatMessage message = messageList.get(position);
            chatAdapter.notifyItemChanged(position);
            setupReplyPreview(message, false);
        });
        ItemTouchHelper itemTouchHelper = new ItemTouchHelper(swipeController);
        itemTouchHelper.attachToRecyclerView(chatRecyclerView);

        chatRecyclerView.addOnLayoutChangeListener((v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (bottom < oldBottom && !messageList.isEmpty()) {
                chatRecyclerView.postDelayed(() -> chatRecyclerView.scrollToPosition(messageList.size() - 1), 100);
            }
        });

        etMessageInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { updateInputUI(); }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(v -> showAttachmentMenu());

        btnSend.setOnClickListener(v -> {
            String text = etMessageInput.getText().toString().trim();
            if (selectedImageUri != null) {
                uploadImageToCloudinaryAndSend(selectedImageUri, text);
                selectedImageUri = null;
                imagePreviewContainer.setVisibility(View.GONE);
                etMessageInput.setText("");
                updateInputUI();
            } else if (!text.isEmpty()) {
                if (editingMessageId != null) {
                    String editedId = editingMessageId; // Сохраняем ID перед очисткой
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("text", text);

                    // Восстанавливаем цитату, если она была
                    if (replyingToMessage != null) {
                        updates.put("replyMessageId", replyingToMessage.getMessageId());
                        updates.put("replySenderId", replyingToMessage.getSenderId());
                        updates.put("replyText", replyingToMessage.getText());
                    } else {
                        updates.put("replyMessageId", null);
                        updates.put("replySenderId", null);
                        updates.put("replyText", null);
                    }

                    // Обновляем само сообщение
                    chatRef.child(editedId).updateChildren(updates);

                    // ИСПРАВЛЕНИЕ: Ищем все сообщения, которые цитируют это, и обновляем в них текст
                    chatRef.orderByChild("replyMessageId").equalTo(editedId)
                            .addListenerForSingleValueEvent(new ValueEventListener() {
                                @Override
                                public void onDataChange(@NonNull DataSnapshot snapshot) {
                                    for (DataSnapshot child : snapshot.getChildren()) {
                                        child.getRef().child("replyText").setValue(text);
                                    }
                                }
                                @Override public void onCancelled(@NonNull DatabaseError error) {}
                            });

                    closeReplyPreview(); // Это сбросит editingMessageId и очистит поле
                } else {
                    etMessageInput.setText("");
                    sendTextMessage(text);
                }
                updateInputUI();
            }
        });
    }

    private void scrollToAndHighlightMessage(String messageId) {
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId() != null && messageList.get(i).getMessageId().equals(messageId)) {
                chatRecyclerView.smoothScrollToPosition(i);
                new Handler().postDelayed(() -> chatAdapter.highlightMessage(messageId), 400);
                break;
            }
        }
    }

    private void loadPinnedMessage() {
        pinnedListener = pinnedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String pinnedId = snapshot.getValue(String.class);
                    chatRef.child(pinnedId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot msgSnap) {
                            if (msgSnap.exists()) {
                                ChatMessage msg = msgSnap.getValue(ChatMessage.class);
                                if (msg != null) {
                                    pinnedMessageContainer.setVisibility(View.VISIBLE);
                                    String text = "text".equals(msg.getType()) ? msg.getText() : ("image".equals(msg.getType()) ? "📷 Фотография" : "🗺️ Воспоминание");
                                    tvPinnedText.setText(text);
                                    pinnedMessageContainer.setOnClickListener(v -> scrollToAndHighlightMessage(pinnedId));
                                }
                            } else {
                                pinnedRef.removeValue();
                            }
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
                } else {
                    pinnedMessageContainer.setVisibility(View.GONE);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (ev.getAction() == android.view.MotionEvent.ACTION_DOWN) {
            startX = ev.getX();
            startY = ev.getY();
        } else if (ev.getAction() == android.view.MotionEvent.ACTION_UP) {
            float endX = ev.getX();
            float endY = ev.getY();
            float density = getResources().getDisplayMetrics().density;

            // Если начали свайп с левого края (меньше 40dp), провели вправо больше 80dp и не сильно ушли вверх/вниз
            if (startX < 40 * density && (endX - startX) > 80 * density && Math.abs(endY - startY) < 60 * density) {
                finish();
                overridePendingTransition(android.R.anim.slide_in_left, android.R.anim.slide_out_right);
                return true;
            }
        }
        return super.dispatchTouchEvent(ev);
    }

    private void setupReplyPreview(ChatMessage message, boolean isEditing) {
        VibratorHelper.vibrate(this, 30);
        replyingToMessage = message;
        replyPreviewContainer.setVisibility(View.VISIBLE);

        if (!isEditing) {
            editingMessageId = null;
            // Я УДАЛИЛ ОТСЮДА etMessageInput.setText(""); ТЕПЕРЬ ТЕКСТ НЕ СТИРАЕТСЯ
        }

        String sender = message.getSenderId().equals(currentUserId) ? "Вы" : tvChatUsername.getText().toString();
        tvReplySender.setText(isEditing ? "Редактирование" : sender);

        String previewText = "";
        if ("text".equals(message.getType())) previewText = message.getText();
        else if ("image".equals(message.getType())) previewText = "📷 Фотография";
        else if ("post".equals(message.getType())) previewText = "🗺️ Воспоминание";
        tvReplyText.setText(previewText);

        etMessageInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etMessageInput, InputMethodManager.SHOW_IMPLICIT);
        updateInputUI();
    }

    private void closeReplyPreview() {
        replyingToMessage = null;
        editingMessageId = null;
        replyPreviewContainer.setVisibility(View.GONE);
        etMessageInput.setText("");
        updateInputUI();
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
        view.findViewById(R.id.btnAttachGallery).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            pickImageLauncher.launch("image/*");
        });
        view.findViewById(R.id.btnAttachPost).setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            pickPostLauncher.launch(new Intent(ChatActivity.this, PickPostActivity.class));
        });
        bottomSheetDialog.show();
    }

    private void setupLaunchers() {
        pickPostLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                String selectedPostId = result.getData().getStringExtra("selectedPostId");
                if (selectedPostId != null) sendPostMessage(selectedPostId);
            }
        });
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                imagePreviewContainer.setVisibility(View.VISIBLE);
                Glide.with(this).load(uri).into(ivPreviewImage);
                updateInputUI();
                etMessageInput.requestFocus();
            }
        });
    }

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
                    if (!isFinishing() && !isDestroyed()) sendImageMessage(imageUrl, caption);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> progressDialog.dismiss());
            } finally {
                if (inputStream != null) { try { inputStream.close(); } catch (Exception e) { e.printStackTrace(); } }
            }
        });
    }

    private void attachReplyDataToMessage(ChatMessage message) {
        if (replyingToMessage != null) {
            message.setReplyMessageId(replyingToMessage.getMessageId());
            message.setReplySenderId(replyingToMessage.getSenderId());
            String replyTxt = "";
            if ("text".equals(replyingToMessage.getType())) replyTxt = replyingToMessage.getText();
            else if ("image".equals(replyingToMessage.getType())) replyTxt = "📷 Фотография";
            else if ("post".equals(replyingToMessage.getType())) replyTxt = "🗺️ Воспоминание";
            message.setReplyText(replyTxt);
        }
    }

    private void sendImageMessage(String imageUrl, String caption) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, imageUrl, caption, System.currentTimeMillis(), "image");
            message.setMessageId(messageId);
            attachReplyDataToMessage(message);
            chatRef.child(messageId).setValue(message);
            closeReplyPreview();
        }
    }

    private void sendTextMessage(String text) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, text, System.currentTimeMillis(), "text");
            message.setMessageId(messageId);
            attachReplyDataToMessage(message);
            chatRef.child(messageId).setValue(message);
            closeReplyPreview();
        }
    }

    private void sendPostMessage(String postId) {
        String messageId = chatRef.push().getKey();
        if (messageId != null) {
            ChatMessage message = new ChatMessage(currentUserId, targetUserId, postId, System.currentTimeMillis());
            message.setMessageId(messageId);
            attachReplyDataToMessage(message);
            chatRef.child(messageId).setValue(message);
            closeReplyPreview();
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

                    String finalUsername = TextUtils.isEmpty(username) ? "Пользователь" : username;
                    tvChatUsername.setText(finalUsername);
                    if (chatAdapter != null) chatAdapter.setTargetUserName(finalUsername);

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(ChatActivity.this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(ivChatAvatar);
                    }
                    String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                    tvChatStatus.setText(statusText);
                    if ("в сети".equals(statusText)) tvChatStatus.setTextColor(getResources().getColor(R.color.online_indicator));
                    else tvChatStatus.setTextColor(getResources().getColor(R.color.text_secondary));
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
                    if (msg.getReceiverId().equals(currentUserId) && !msg.isRead()) {
                        snapshot.getRef().child("read").setValue(true);
                        msg.setRead(true);
                    }
                    messageList.add(msg);
                    chatAdapter.notifyItemInserted(messageList.size() - 1);
                    chatRecyclerView.scrollToPosition(messageList.size() - 1);
                    emptyChatContainer.setVisibility(View.GONE);
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
                if (messageList.isEmpty()) emptyChatContainer.setVisibility(View.VISIBLE);
            }
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        chatRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) emptyChatContainer.setVisibility(View.VISIBLE);
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateMyStatus(Object status) {
        SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, MODE_PRIVATE);
        boolean hideOnline = prefs.getBoolean("privacy_hide_online", false);
        if (hideOnline) myStatusRef.setValue("hidden");
        else {
            myStatusRef.setValue(status);
            myStatusRef.onDisconnect().setValue(System.currentTimeMillis());
        }
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
        if (pinnedRef != null && pinnedListener != null) pinnedRef.removeEventListener(pinnedListener);
    }
}
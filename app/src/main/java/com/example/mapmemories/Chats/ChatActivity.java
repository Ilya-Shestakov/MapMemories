package com.example.mapmemories.Chats;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import android.app.NotificationManager;
import android.content.Context;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.example.mapmemories.Profile.User;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.example.mapmemories.systemHelpers.AudioPlayerManager;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.File;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;

public class ChatActivity extends AppCompatActivity {

    /* |-----------------------------------------------------------------------|
     * |                           ПЕРЕМЕННЫЕ                              |
     * |-----------------------------------------------------------------------| */

    public static String currentChatUserId = null;

    private float startX, startY;
    private String targetUserId, currentUserId, chatId;
    private String editingMessageId = null;
    private Uri selectedImageUri = null;
    private ChatMessage replyingToMessage = null;

    private FrameLayout galleryContainer;
    private androidx.viewpager2.widget.ViewPager2 galleryViewPager;
    private TextView tvGalleryCounter;
    private ImageButton btnGalleryClose, btnGalleryMenu;
    private List<ChatMessage> galleryImages = new ArrayList<>();
    private GalleryAdapter galleryAdapter;
    private boolean isGalleryOpen = false;


    private LinearLayout userInfoContainer, emptyChatContainer, pinnedMessageContainer;
    private ImageView ivChatAvatar;
    private TextView tvChatUsername, tvChatStatus, btnSayHello, tvPinnedText;
    private ImageButton btnUnpin;

    private View rootLayout;
    private boolean isSwipingToClose = false;
    private boolean canSwipeBack = false;
    private int screenWidth;

    private RecyclerView chatRecyclerView;
    private ChatAdapter chatAdapter;
    private List<ChatMessage> messageList;
    private FloatingActionButton fabScrollDown;

    private LinearLayout textInputContainer, recordingContainer;
    private ImageButton btnAttach, btnSend, btnRemoveImage;
    private EditText etMessageInput;
    private ConstraintLayout imagePreviewContainer, replyPreviewContainer;
    private ImageView ivPreviewImage;
    private TextView tvReplySender, tvReplyText;
    private ImageButton btnCloseReply;

    public boolean isAudioManuallyPaused = false;

    private LinearLayout selectionToolbar;
    private TextView tvSelectedCount;
    private ImageButton btnCloseSelection, btnSelectionReact, btnSelectionCopy, btnSelectionForward, btnSelectionDelete;

    private DatabaseReference chatRef, targetUserRef, myStatusRef, pinnedRef, typingRef;
    private ValueEventListener statusListener, pinnedListener, typingListener;
    private ChildEventListener messagesListener;
    private Handler typingHandler = new Handler();
    private Runnable typingRunnable;

    private ActivityResultLauncher<String> pickImageLauncher;
    private ActivityResultLauncher<String> requestMicLauncher;

    private Cloudinary cloudinary;
    private Map<String, java.util.concurrent.Future<?>> uploadTasks = new HashMap<>();
    private boolean isTargetUserHidden = false;

    // ПЕРЕМЕННЫЕ ЗАПИСИ ГС
    private ImageButton btnRecordVoice;
    private LinearLayout lockOverlay;
    private TextView tvRecordTime, tvSlideToCancel, btnCancelVoiceLock;
    private View redDot;
    private MediaRecorder mediaRecorder;
    private String audioFilePath;
    private boolean isRecording = false;
    private boolean isRecordingLocked = false;
    private long recordStartTime;
    private Handler timerHandler = new Handler();

    private int touchSlop;

    private View globalPlayerContainer;
    private View gpExpandedControls;
    private ImageView gpIcon;
    private TextView gpTitle, gpCurrentTime, gpTotalTime;
    private ImageButton gpClose, gpDownload;
    private android.widget.SeekBar gpSeekBar;
    private boolean isGlobalPlayerExpanded = false;

    /* |-----------------------------------------------------------------------|
     * |                           ЖИЗНЕННЫЙ ЦИКЛ                          |
     * |-----------------------------------------------------------------------| */

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
        typingRef = FirebaseDatabase.getInstance().getReference("chats").child(chatId).child("typingStates");
        targetUserRef = FirebaseDatabase.getInstance().getReference("users").child(targetUserId);
        myStatusRef = FirebaseDatabase.getInstance().getReference("users").child(currentUserId).child("status");

        screenWidth = getResources().getDisplayMetrics().widthPixels;

        touchSlop = android.view.ViewConfiguration.get(this).getScaledTouchSlop();


        initCloudinary();
        initViews();
        setupLaunchers();
        setupVoiceRecording();

        loadTargetUserData();
        loadMessagesOptimized();
        loadPinnedMessage();

        clearNotification();

    }

    /* |-----------------------------------------------------------------------|
     * |                           ИНИЦИАЛИЗАЦИЯ                           |
     * |-----------------------------------------------------------------------| */

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    private void initViews() {
        rootLayout = findViewById(R.id.rootLayout);
        userInfoContainer = findViewById(R.id.userInfoContainer);
        ivChatAvatar = findViewById(R.id.ivChatAvatar);
        tvChatUsername = findViewById(R.id.tvChatUsername);
        tvChatStatus = findViewById(R.id.tvChatStatus);
        emptyChatContainer = findViewById(R.id.emptyChatContainer);
        btnSayHello = findViewById(R.id.btnSayHello);
        pinnedMessageContainer = findViewById(R.id.pinnedMessageContainer);
        tvPinnedText = findViewById(R.id.tvPinnedText);
        btnUnpin = findViewById(R.id.btnUnpin);

        selectionToolbar = findViewById(R.id.selectionToolbar);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        btnCloseSelection = findViewById(R.id.btnCloseSelection);
        btnSelectionReact = findViewById(R.id.btnSelectionReact);
        btnSelectionCopy = findViewById(R.id.btnSelectionCopy);
        btnSelectionForward = findViewById(R.id.btnSelectionForward);
        btnSelectionDelete = findViewById(R.id.btnSelectionDelete);

        chatRecyclerView = findViewById(R.id.chatRecyclerView);
        chatRecyclerView.setItemAnimator(new DefaultItemAnimator());
        fabScrollDown = findViewById(R.id.fabScrollDown);

        textInputContainer = findViewById(R.id.textInputContainer);
        recordingContainer = findViewById(R.id.recordingContainer);

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

        btnRecordVoice = findViewById(R.id.btnRecordVoice);
        lockOverlay = findViewById(R.id.lockOverlay);
        tvRecordTime = findViewById(R.id.tvRecordTime);
        tvSlideToCancel = findViewById(R.id.tvSlideToCancel);
        btnCancelVoiceLock = findViewById(R.id.btnCancelVoiceLock);
        redDot = findViewById(R.id.redDot);

        globalPlayerContainer = findViewById(R.id.globalPlayerContainer);
        gpExpandedControls = findViewById(R.id.gpExpandedControls);
        gpIcon = findViewById(R.id.gpIcon);
        gpTitle = findViewById(R.id.gpTitle);
        gpCurrentTime = findViewById(R.id.gpCurrentTime);
        gpTotalTime = findViewById(R.id.gpTotalTime);
        gpClose = findViewById(R.id.gpClose);
        gpDownload = findViewById(R.id.gpDownload);
        gpSeekBar = findViewById(R.id.gpSeekBar);

        galleryContainer = findViewById(R.id.galleryContainer);
        galleryViewPager = findViewById(R.id.galleryViewPager);
        tvGalleryCounter = findViewById(R.id.tvGalleryCounter);
        btnGalleryClose = findViewById(R.id.btnGalleryClose);
        btnGalleryMenu = findViewById(R.id.btnGalleryMenu);

        btnGalleryClose.setOnClickListener(v -> closeGallery());

        setupGlobalPlayer();

        setupSelectionActions();

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayShowTitleEnabled(false);
        toolbar.setNavigationOnClickListener(v -> getOnBackPressedDispatcher().onBackPressed());

        userInfoContainer.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Intent intent = new Intent(ChatActivity.this, UserProfileActivity.class);
            intent.putExtra("targetUserId", targetUserId);
            startActivity(intent);
        });

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
        chatAdapter = new ChatAdapter(this, messageList, createChatActionListener());
        chatRecyclerView.setAdapter(chatAdapter);

        final boolean[] isAudioCompleting = {false};

        AudioPlayerManager.getInstance().setCallback(new AudioPlayerManager.PlayerCallback() {
            @Override
            public void onStateChanged(String messageId, boolean isPlaying) {
                chatAdapter.updateAudioState(messageId, isPlaying);

                if (isPlaying) {
                    if (globalPlayerContainer.getVisibility() == View.GONE) {
                        globalPlayerContainer.setVisibility(View.VISIBLE);
                        globalPlayerContainer.setTranslationY(-100f);
                        globalPlayerContainer.setAlpha(0f);
                        globalPlayerContainer.animate().translationY(0f).alpha(1f).setDuration(300).setInterpolator(new OvershootInterpolator()).start();
                    }
                    gpIcon.setImageResource(android.R.drawable.ic_media_pause);
                } else {
                    gpIcon.setImageResource(android.R.drawable.ic_media_play);

                    // ПУЛЕНЕПРОБИВАЕМАЯ ПРОВЕРКА:
                    // Если трек остановился, и МЫ НЕ ЖАЛИ ПАУЗУ РУКАМИ - значит трек завершился!
                    if (!isAudioManuallyPaused) {
                        boolean hasNext = playNextVoiceMessage(messageId);
                        // Если следующего ГС нет - сворачиваем верхнее окно
                        if (!hasNext) {
                            closeGlobalPlayer();
                        }
                    }
                }
            }

            @Override
            public void onProgressUpdate(String messageId, int currentPos, int maxDuration) {
                gpSeekBar.setMax(maxDuration);
                gpSeekBar.setProgress(currentPos);
                gpCurrentTime.setText(formatTimeStr(currentPos));
                gpTotalTime.setText(formatTimeStr(maxDuration));
            }

            @Override public void onError(String error) { Toast.makeText(ChatActivity.this, error, Toast.LENGTH_SHORT).show(); }
        });

        chatRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                if (!recyclerView.canScrollVertically(1)) fabScrollDown.setVisibility(View.GONE);
                else if (dy < 0) fabScrollDown.setVisibility(View.VISIBLE);
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
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateInputUI();
                typingRef.child(currentUserId).setValue("typing");
                typingHandler.removeCallbacks(typingRunnable);
                typingRunnable = () -> typingRef.child(currentUserId).setValue("false");
                typingHandler.postDelayed(typingRunnable, 2000);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        btnAttach.setOnClickListener(this::showAttachmentMenuPopup);

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
                    String editedId = editingMessageId;
                    Map<String, Object> updates = new HashMap<>();
                    updates.put("text", text);

                    if (replyingToMessage != null) {
                        updates.put("replyMessageId", replyingToMessage.getMessageId());
                        updates.put("replySenderId", replyingToMessage.getSenderId());
                        updates.put("replyText", replyingToMessage.getText());
                    } else {
                        updates.put("replyMessageId", null);
                        updates.put("replySenderId", null);
                        updates.put("replyText", null);
                    }

                    chatRef.child(editedId).updateChildren(updates);
                    chatRef.orderByChild("replyMessageId").equalTo(editedId).addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                            for (DataSnapshot child : snapshot.getChildren()) child.getRef().child("replyText").setValue(text);
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
                    closeReplyPreview();
                } else {
                    etMessageInput.setText("");
                    sendTextMessage(text);
                }
                updateInputUI();
            }
        });
    }

    private void closeGlobalPlayer() {
        AudioPlayerManager.getInstance().stop();
        if (globalPlayerContainer.getVisibility() == View.VISIBLE) {
            globalPlayerContainer.animate()
                    .translationY(-100f)
                    .alpha(0f)
                    .setDuration(200)
                    .withEndAction(() -> {
                        globalPlayerContainer.setVisibility(View.GONE);
                        isGlobalPlayerExpanded = false;
                        gpExpandedControls.setVisibility(View.GONE);
                    }).start();
        }
    }

    private void setupLaunchers() {
        pickImageLauncher = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                selectedImageUri = uri;
                imagePreviewContainer.setVisibility(View.VISIBLE);
                Glide.with(this).load(uri).into(ivPreviewImage);
                updateInputUI();
                etMessageInput.requestFocus();
            }
        });
        requestMicLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (!isGranted) Toast.makeText(this, "Для отправки ГС нужен микрофон", Toast.LENGTH_SHORT).show();
        });
    }

    private void setupGlobalPlayer() {
        // Анимация раскрытия/сжатия по клику на саму панель
        globalPlayerContainer.setOnClickListener(v -> {
            // Вот она, магия перетекания! Одна строчка делает всю жидкую анимацию.
            android.transition.TransitionManager.beginDelayedTransition((ViewGroup) globalPlayerContainer.getParent(), new android.transition.AutoTransition().setDuration(250));

            isGlobalPlayerExpanded = !isGlobalPlayerExpanded;
            gpExpandedControls.setVisibility(isGlobalPlayerExpanded ? View.VISIBLE : View.GONE);
        });

        // Кнопка Пауза/Плей в самом глобальном плеере
        gpIcon.setOnClickListener(v -> {
            if (AudioPlayerManager.getInstance().isPlaying()) {
                isAudioManuallyPaused = true; // СТАВИМ ФЛАГ - ПАУЗА НАЖАТА РУКАМИ
                AudioPlayerManager.getInstance().pause();
                gpIcon.setImageResource(android.R.drawable.ic_media_play);
            } else {
                isAudioManuallyPaused = false; // СНИМАЕМ ФЛАГ
                String currentPlayingId = AudioPlayerManager.getInstance().getCurrentPlayingId();
                if (currentPlayingId != null) {
                    for (ChatMessage msg : messageList) {
                        if (msg.getMessageId().equals(currentPlayingId)) {
                            AudioPlayerManager.getInstance().play(currentPlayingId, msg.getImageUrl());
                            break;
                        }
                    }
                }
            }
        });

        // Закрытие панели
        gpClose.setOnClickListener(v -> {
            AudioPlayerManager.getInstance().stop();
            globalPlayerContainer.animate().translationY(-100f).alpha(0f).setDuration(200).withEndAction(() -> {
                globalPlayerContainer.setVisibility(View.GONE);
                isGlobalPlayerExpanded = false;
                gpExpandedControls.setVisibility(View.GONE);
            }).start();
        });

        // Перемотка
        gpSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) AudioPlayerManager.getInstance().seekTo(progress);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Заглушка скачивания
        gpDownload.setOnClickListener(v -> Toast.makeText(this, "Скачивание аудио...", Toast.LENGTH_SHORT).show());
    }

    private String formatTimeStr(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / (1000 * 60)) % 60;
        return String.format(Locale.getDefault(), "%d:%02d", minutes, seconds);
    }

    /* |-----------------------------------------------------------------------|
     * |                           ЛОГИКА АДАПТЕРА                         |
     * |-----------------------------------------------------------------------| */

    private ChatAdapter.ChatActionListener createChatActionListener() {
        return new ChatAdapter.ChatActionListener() {
            @Override
            public void onSelectionChanged(int selectedCount) {
                if (selectedCount > 0) {
                    if (selectionToolbar.getVisibility() == View.GONE) {
                        selectionToolbar.setVisibility(View.VISIBLE);
                        selectionToolbar.setAlpha(0f);
                        selectionToolbar.setTranslationY(-50f);
                        selectionToolbar.animate().alpha(1f).translationY(0f).setDuration(200).start();
                    }
                    tvSelectedCount.setText("Выбрано: " + selectedCount);
                } else {
                    selectionToolbar.animate().alpha(0f).translationY(-50f).setDuration(200).withEndAction(() -> selectionToolbar.setVisibility(View.GONE)).start();
                }
            }
            @Override
            public void onReactionSelected(ChatMessage message, String reaction) {
                VibratorHelper.vibrate(ChatActivity.this, 20);
                if (reaction == null) chatRef.child(message.getMessageId()).child("reaction").removeValue();
                else chatRef.child(message.getMessageId()).child("reaction").setValue(reaction);
            }
            @Override
            public void onEditMessage(ChatMessage message) {
                editingMessageId = message.getMessageId();
                etMessageInput.setText(message.getText());
                etMessageInput.setSelection(message.getText().length());
                replyPreviewContainer.setVisibility(View.VISIBLE);
                tvReplySender.setText("Редактирование");
                tvReplyText.setText(message.getText());
                updateInputUI();
                etMessageInput.requestFocus();
                InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(etMessageInput, InputMethodManager.SHOW_IMPLICIT);
            }
            @Override
            public void onDeleteMessage(ChatMessage message, boolean isMine) {
                showPremiumDeleteDialog(message, isMine);
            }
            @Override
            public void onPinMessage(ChatMessage message) { pinnedRef.setValue(message.getMessageId()); }
            @Override
            public void onMessageHighlighted(String messageId) { VibratorHelper.vibrate(ChatActivity.this, 20); }
            @Override
            public void onReplyMessage(ChatMessage message) { setupReplyPreview(message, false); }
            @Override
            public void onQuoteClicked(String messageId) { scrollToAndHighlightMessage(messageId); }
            @Override
            public void onCancelUpload(String messageId) {
                java.util.concurrent.Future<?> task = uploadTasks.get(messageId);
                if (task != null) { task.cancel(true); uploadTasks.remove(messageId); }
                removeTempMessageLocally(messageId);
            }
            @Override
            public void onImageClicked(View thumbView, ChatMessage message) {
                openGallery(thumbView, message);
            }
        };
    }

    private void showPremiumDeleteDialog(ChatMessage message, boolean isMine) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View activityRootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) {
            activityRootView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.MIRROR));
        }

        FrameLayout rootLayout = new FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#66000000"));
        rootLayout.setAlpha(0f);

        MaterialCardView cardView = new MaterialCardView(this);
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.primary)); // ИСПРАВЛЕНО
        cardView.setRadius(48f);
        cardView.setCardElevation(20f);
        FrameLayout.LayoutParams cardParams = new FrameLayout.LayoutParams(
                (int) (screenWidth * 0.85), ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = Gravity.CENTER;
        cardView.setLayoutParams(cardParams);

        LinearLayout cardContent = new LinearLayout(this);
        cardContent.setOrientation(LinearLayout.VERTICAL);
        cardContent.setPadding(64, 64, 64, 48);

        TextView title = new TextView(this);
        title.setText("Удалить сообщение?");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setGravity(Gravity.CENTER);
        cardContent.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Это действие нельзя будет отменить.");
        desc.setTextSize(14f);
        desc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        desc.setGravity(Gravity.CENTER);
        desc.setPadding(0, 16, 0, 48);
        cardContent.addView(desc);

        final boolean[] deleteForEveryone = {false};
        if (isMine) {
            LinearLayout toggleRow = new LinearLayout(this);
            toggleRow.setOrientation(LinearLayout.HORIZONTAL);
            toggleRow.setGravity(Gravity.CENTER_VERTICAL);
            toggleRow.setPadding(32, 24, 32, 24);

            GradientDrawable toggleBg = new GradientDrawable();
            toggleBg.setShape(GradientDrawable.RECTANGLE);
            toggleBg.setCornerRadius(16f);
            toggleBg.setColor(Color.parseColor("#1A000000"));
            toggleRow.setBackground(toggleBg);

            ImageView tickIcon = new ImageView(this);
            tickIcon.setImageResource(R.drawable.ic_check);
            tickIcon.setColorFilter(Color.GRAY);
            tickIcon.setLayoutParams(new LinearLayout.LayoutParams(48, 48));

            TextView toggleText = new TextView(this);
            toggleText.setText("Удалить также у " + tvChatUsername.getText());
            toggleText.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
            toggleText.setPadding(24, 0, 0, 0);

            toggleRow.addView(tickIcon);
            toggleRow.addView(toggleText);
            cardContent.addView(toggleRow);

            toggleRow.setOnClickListener(v -> {
                deleteForEveryone[0] = !deleteForEveryone[0];
                if (deleteForEveryone[0]) {
                    toggleBg.setColor(Color.parseColor("#33FF5252"));
                    tickIcon.setColorFilter(Color.parseColor("#FF5252"));
                } else {
                    toggleBg.setColor(Color.parseColor("#1A000000"));
                    tickIcon.setColorFilter(Color.GRAY);
                }
            });
        }

        LinearLayout btnContainer = new LinearLayout(this);
        btnContainer.setOrientation(LinearLayout.HORIZONTAL);
        btnContainer.setPadding(0, 48, 0, 0);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("Отмена");
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setTextSize(16f);
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btnCancel.setPadding(0, 32, 0, 32);
        btnCancel.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnDelete = new TextView(this);
        btnDelete.setText("Удалить");
        btnDelete.setGravity(Gravity.CENTER);
        btnDelete.setTextSize(16f);
        btnDelete.setTypeface(null, android.graphics.Typeface.BOLD);
        btnDelete.setTextColor(Color.parseColor("#FF5252"));
        btnDelete.setPadding(0, 32, 0, 32);
        btnDelete.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnContainer.addView(btnCancel);
        btnContainer.addView(btnDelete);
        cardContent.addView(btnContainer);
        cardView.addView(cardContent);
        rootLayout.addView(cardView);
        dialog.setContentView(rootLayout);

        rootLayout.animate().alpha(1f).setDuration(200).start();
        cardView.setScaleX(0.8f); cardView.setScaleY(0.8f);
        cardView.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new OvershootInterpolator(1.2f)).start();

        Runnable closeDialog = () -> {
            rootLayout.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) {
                    activityRootView.setRenderEffect(null);
                }
                dialog.dismiss();
            }).start();
        };

        btnCancel.setOnClickListener(v -> closeDialog.run());
        btnDelete.setOnClickListener(v -> {
            closeDialog.run();
            if (isMine && deleteForEveryone[0]) chatRef.child(message.getMessageId()).removeValue();
            else chatRef.child(message.getMessageId()).child("deletedBy").setValue(currentUserId);
        });
        rootLayout.setOnClickListener(v -> closeDialog.run());

        dialog.setOnCancelListener(d -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) activityRootView.setRenderEffect(null);
        });
        dialog.show();
    }

    private void setupSelectionActions() {
        btnCloseSelection.setOnClickListener(v -> chatAdapter.clearSelection());

        btnSelectionCopy.setOnClickListener(v -> {
            Set<String> selectedIds = chatAdapter.getSelectedMessageIds();
            StringBuilder sb = new StringBuilder();
            for (ChatMessage msg : messageList) {
                if (selectedIds.contains(msg.getMessageId()) && "text".equals(msg.getType())) {
                    sb.append(msg.getText()).append("\n");
                }
            }
            if (sb.length() > 0) {
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Copied Messages", sb.toString().trim());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Текст скопирован", Toast.LENGTH_SHORT).show();
            } else Toast.makeText(this, "Нет текста", Toast.LENGTH_SHORT).show();
            chatAdapter.clearSelection();
        });

        btnSelectionDelete.setOnClickListener(v -> {
            for (String id : chatAdapter.getSelectedMessageIds()) chatRef.child(id).child("deletedBy").setValue(currentUserId);
            chatAdapter.clearSelection();
            Toast.makeText(this, "Удалено у вас", Toast.LENGTH_SHORT).show();
        });

        btnSelectionReact.setOnClickListener(v -> {
            LinearLayout menuLayout = new LinearLayout(this);
            menuLayout.setOrientation(LinearLayout.HORIZONTAL);
            menuLayout.setPadding(16, 16, 16, 16);
            menuLayout.setBackgroundResource(R.drawable.bg_telegram_popup);

            String[] emojis = {"👍", "❤️", "😂", "😢", "😡", "🎉"};
            for (String emoji : emojis) {
                TextView tvEmoji = new TextView(this);
                tvEmoji.setText(emoji);
                tvEmoji.setTextSize(28f);
                tvEmoji.setPadding(16, 16, 16, 16);

                tvEmoji.setOnTouchListener((view, event) -> {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN: view.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).start(); break;
                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL: view.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(new OvershootInterpolator()).start(); break;
                    }
                    return false;
                });

                menuLayout.addView(tvEmoji);
            }

            PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
            popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            popupWindow.setElevation(16f);
            popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

            for(int i=0; i<menuLayout.getChildCount(); i++){
                View child = menuLayout.getChildAt(i);
                child.setOnClickListener(click -> {
                    popupWindow.dismiss();
                    for (String id : chatAdapter.getSelectedMessageIds()) chatRef.child(id).child("reaction").setValue(((TextView)child).getText().toString());
                    chatAdapter.clearSelection();
                });
            }

            menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
            popupWindow.showAsDropDown(btnSelectionReact, -menuLayout.getMeasuredWidth() / 2, 20);
        });

        btnSelectionForward.setOnClickListener(v -> { /* Твой диалог пересылки */ });
    }

    private void showAttachmentMenuPopup(View anchorView) {
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setPadding(0, 16, 0, 16);
        menuLayout.setBackgroundResource(R.drawable.bg_telegram_popup);

        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);

        TextView btnPhoto = new TextView(this);
        btnPhoto.setText("📷 Фото и Видео");
        btnPhoto.setTextSize(16f);
        btnPhoto.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btnPhoto.setPadding(48, 36, 64, 36);
        btnPhoto.setBackgroundResource(outValue.resourceId);

        menuLayout.addView(btnPhoto);

        PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(16f);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        btnPhoto.setOnClickListener(v -> {
            popupWindow.dismiss();
            pickImageLauncher.launch("image/*");
        });

        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popupWindow.showAsDropDown(anchorView, 0, -menuLayout.getMeasuredHeight() - anchorView.getHeight() - 20);
    }


    /* |-----------------------------------------------------------------------|
     * |                           ГАЛЕРЕЯ И ЗУМ                               |
     * |-----------------------------------------------------------------------| */

    private void openGallery(View thumbView, ChatMessage clickedMessage) {
        if (isGalleryOpen) return;
        isGalleryOpen = true;

        // 1. Собираем все картинки из чата
        galleryImages.clear();
        int startIndex = 0;
        for (ChatMessage msg : messageList) {
            if ("image".equals(msg.getType()) && msg.getImageUrl() != null) {
                if (msg.getMessageId().equals(clickedMessage.getMessageId())) {
                    startIndex = galleryImages.size();
                }
                galleryImages.add(msg);
            }
        }

        // 2. Настраиваем адаптер
        galleryAdapter = new GalleryAdapter(galleryImages);
        galleryViewPager.setAdapter(galleryAdapter);
        galleryViewPager.setCurrentItem(startIndex, false);
        updateGalleryCounter(startIndex);

        galleryViewPager.registerOnPageChangeCallback(new androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateGalleryCounter(position);
            }
        });

        // 3. Меню опций (3 точки)
        btnGalleryMenu.setOnClickListener(v -> showGalleryMenu(v, galleryImages.get(galleryViewPager.getCurrentItem())));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.graphics.RenderEffect blurEffect = android.graphics.RenderEffect.createBlurEffect(30f, 30f, android.graphics.Shader.TileMode.MIRROR);
            findViewById(R.id.appBarLayout).setRenderEffect(blurEffect);
            findViewById(R.id.chatRecyclerView).setRenderEffect(blurEffect);
            findViewById(R.id.bottomInputContainer).setRenderEffect(blurEffect);
        }

        // Сброс фона и тулбара (на случай если закрыли свайпом)
        galleryContainer.setBackgroundColor(Color.BLACK);
        findViewById(R.id.galleryToolbar).setAlpha(1f);

        // Плавное появление
        galleryContainer.setAlpha(0f);
        galleryContainer.setVisibility(View.VISIBLE);
        galleryContainer.animate()
                .alpha(1f)
                .setDuration(250)
                .start();
    }

    private void closeGallery() {
        if (!isGalleryOpen) return;

        // Анимация исчезновения
        galleryContainer.animate()
                .alpha(0f)
                .setDuration(200)
                .withEndAction(() -> {
                    galleryContainer.setVisibility(View.GONE);
                    isGalleryOpen = false;

                    // СНИМАЕМ БЛЮР
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        findViewById(R.id.appBarLayout).setRenderEffect(null);
                        findViewById(R.id.chatRecyclerView).setRenderEffect(null);
                        findViewById(R.id.bottomInputContainer).setRenderEffect(null);
                    }

                    // Возвращаем картинку в нормальное состояние (если закрыли свайпом)
                    if (galleryViewPager.getChildAt(0) != null) {
                        View currentView = galleryViewPager.getChildAt(0);
                        com.github.chrisbanes.photoview.PhotoView photoView = currentView.findViewById(R.id.photoView);
                        if (photoView != null) {
                            photoView.setTranslationY(0f);
                            photoView.setScaleX(1f);
                            photoView.setScaleY(1f);
                        }
                    }
                }).start();
    }

    private void updateGalleryCounter(int position) {
        tvGalleryCounter.setText((position + 1) + " из " + galleryImages.size());
    }

    // Меню фич для картинки
    private void showGalleryMenu(View anchor, ChatMessage currentMessage) {
        LinearLayout menuLayout = new LinearLayout(this);
        menuLayout.setOrientation(LinearLayout.VERTICAL);
        menuLayout.setPadding(0, 16, 0, 16);
        menuLayout.setBackgroundResource(R.drawable.bg_telegram_popup); // Ваш фон для меню

        // Кнопка Скачать
        TextView btnDownload = createMenuTextView("💾 Сохранить в галерею");
        // Кнопка Поделиться
        TextView btnShare = createMenuTextView("📤 Поделиться");

        menuLayout.addView(btnDownload);
        menuLayout.addView(btnShare);

        PopupWindow popupWindow = new PopupWindow(menuLayout, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(20f);
        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);

        btnDownload.setOnClickListener(v -> {
            popupWindow.dismiss();
            downloadImage(currentMessage.getImageUrl());
        });

        btnShare.setOnClickListener(v -> {
            popupWindow.dismiss();
            shareImage(currentMessage.getImageUrl());
        });

        menuLayout.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        popupWindow.showAsDropDown(anchor, -menuLayout.getMeasuredWidth() + anchor.getWidth(), 20);
    }

    private TextView createMenuTextView(String text) {
        TextView textView = new TextView(this);
        textView.setText(text);
        textView.setTextSize(16f);
        textView.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        textView.setPadding(48, 32, 64, 32);
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);
        return textView;
    }

    // Функция скачивания
    private void downloadImage(String imageUrl) {
        android.app.DownloadManager downloadManager = (android.app.DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        Uri uri = Uri.parse(imageUrl);
        android.app.DownloadManager.Request request = new android.app.DownloadManager.Request(uri);
        request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "MapMemories_" + System.currentTimeMillis() + ".jpg");
        downloadManager.enqueue(request);
        Toast.makeText(this, "Скачивание началось...", Toast.LENGTH_SHORT).show();
    }

    // Функция "Поделиться"
    private void shareImage(String imageUrl) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, "Смотри это фото: " + imageUrl);
        startActivity(Intent.createChooser(shareIntent, "Поделиться фото"));
    }

    /* |-----------------------------------------------------------------------|
     * |                         АДАПТЕР ГАЛЕРЕИ                               |
     * |-----------------------------------------------------------------------| */
    private class GalleryAdapter extends RecyclerView.Adapter<GalleryAdapter.GalleryViewHolder> {
        private List<ChatMessage> images;

        public GalleryAdapter(List<ChatMessage> images) {
            this.images = images;
        }

        @NonNull
        @Override
        public GalleryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_gallery_image, parent, false);
            return new GalleryViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull GalleryViewHolder holder, int position) {
            String url = images.get(position).getImageUrl();

            Glide.with(ChatActivity.this)
                    .load(url)
                    .override(com.bumptech.glide.request.target.Target.SIZE_ORIGINAL)
                    .diskCacheStrategy(com.bumptech.glide.load.engine.DiskCacheStrategy.ALL)
                    .into(holder.photoView);

            // Закрытие по одиночному тапу
            holder.photoView.setOnPhotoTapListener((view, x, y) -> closeGallery());

            // --- ЛОГИКА СВАЙПА ВВЕРХ/ВНИЗ ДЛЯ ЗАКРЫТИЯ ---
            holder.photoView.setOnTouchListener(new View.OnTouchListener() {
                private float startY;
                private boolean isDragging = false;
                private View galleryToolbar = findViewById(R.id.galleryToolbar);

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    // Если фото приближено (зум), не перехватываем свайпы
                    if (holder.photoView.getScale() > 1.0f) return false;

                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            startY = event.getRawY();
                            isDragging = false;
                            break;

                        case MotionEvent.ACTION_MOVE:
                            float dy = event.getRawY() - startY;

                            // Если потянули по вертикали сильнее, чем по горизонтали - начинаем драг
                            if (!isDragging && Math.abs(dy) > 30) {
                                isDragging = true;
                                galleryViewPager.setUserInputEnabled(false); // Отключаем листание ViewPager
                            }

                            if (isDragging) {
                                holder.photoView.setTranslationY(dy);

                                // Вычисляем прозрачность фона (от 1.0 до 0.0)
                                float alpha = 1f - Math.min(Math.abs(dy) / (galleryContainer.getHeight() / 2f), 1f);
                                galleryContainer.setBackgroundColor(Color.argb((int) (alpha * 255), 0, 0, 0));
                                galleryToolbar.setAlpha(alpha); // Прячем тулбар

                                // Слегка уменьшаем картинку при оттягивании (эффект Telegram)
                                float scale = 1f - Math.min(Math.abs(dy) / (galleryContainer.getHeight() * 2f), 0.2f);
                                holder.photoView.setScaleX(scale);
                                holder.photoView.setScaleY(scale);

                                return true;
                            }
                            break;

                        case MotionEvent.ACTION_UP:
                        case MotionEvent.ACTION_CANCEL:
                            if (isDragging) {
                                float finalDy = event.getRawY() - startY;

                                // Если оттянули достаточно далеко - закрываем
                                if (Math.abs(finalDy) > 250) {
                                    closeGallery();
                                } else {
                                    // Иначе возвращаем картинку на место (Snap back)
                                    holder.photoView.animate()
                                            .translationY(0f)
                                            .scaleX(1f)
                                            .scaleY(1f)
                                            .setDuration(200)
                                            .setInterpolator(new OvershootInterpolator(1.0f))
                                            .start();

                                    galleryContainer.setBackgroundColor(Color.BLACK);
                                    galleryToolbar.animate().alpha(1f).setDuration(200).start();
                                }

                                isDragging = false;
                                galleryViewPager.setUserInputEnabled(true); // Включаем листание обратно
                                return true;
                            }
                            break;
                    }
                    return false;
                }
            });
        }

        @Override
        public int getItemCount() {
            return images.size();
        }

        class GalleryViewHolder extends RecyclerView.ViewHolder {
            com.github.chrisbanes.photoview.PhotoView photoView;
            GalleryViewHolder(@NonNull View itemView) {
                super(itemView);
                photoView = itemView.findViewById(R.id.photoView);
            }
        }
    }


    /* |-----------------------------------------------------------------------|
     * |                           ЛОГИКА ЗАПИСИ ГС                        |
     * |-----------------------------------------------------------------------| */

    private void setupVoiceRecording() {
        btnCancelVoiceLock.setOnClickListener(v -> cancelRecording());

        btnRecordVoice.setOnTouchListener(new View.OnTouchListener() {
            float startY, startX;
            boolean isCancelled = false;
            boolean lockedInThisGesture = false; // Отличает свайп вверх от тапа по кнопке
            boolean directionDecided = false;
            boolean movingUp = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        if (isRecordingLocked) {
                            // Кнопка зафиксирована. Мы просто тапнули на нее.
                            btnRecordVoice.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start();
                            return true;
                        }

                        if (ContextCompat.checkSelfPermission(ChatActivity.this, android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            requestMicLauncher.launch(android.Manifest.permission.RECORD_AUDIO);
                            return false;
                        }

                        startY = event.getRawY(); startX = event.getRawX();
                        isCancelled = false;
                        lockedInThisGesture = false; // Сбрасываем флаг при новом старте
                        directionDecided = false;
                        movingUp = false;

                        startRecording();
                        VibratorHelper.vibrate(ChatActivity.this, 50);

                        btnRecordVoice.animate().scaleX(1.5f).scaleY(1.5f).setDuration(200).start();

                        textInputContainer.setVisibility(View.GONE);
                        recordingContainer.setVisibility(View.VISIBLE);

                        lockOverlay.setVisibility(View.VISIBLE);
                        lockOverlay.setAlpha(0f);
                        lockOverlay.animate().alpha(1f).translationY(0).setDuration(200).start();

                        tvSlideToCancel.setVisibility(View.VISIBLE);
                        tvSlideToCancel.setAlpha(1f);
                        btnCancelVoiceLock.setVisibility(View.GONE);
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isRecordingLocked || isCancelled || !isRecording) break;

                        float dy = startY - event.getRawY();
                        float dx = startX - event.getRawX();

                        if (!directionDecided && (Math.abs(dy) > 20 || Math.abs(dx) > 20)) {
                            directionDecided = true;
                            movingUp = Math.abs(dy) > Math.abs(dx);
                        }

                        if (directionDecided) {
                            if (movingUp) {
                                if (dy > 150) {
                                    lockedInThisGesture = true; // Мы залочили запись свайпом!
                                    isRecordingLocked = true;
                                    VibratorHelper.vibrate(ChatActivity.this, 30);

                                    lockOverlay.animate().alpha(0f).translationY(-50).setDuration(200).withEndAction(() -> lockOverlay.setVisibility(View.GONE)).start();
                                    tvSlideToCancel.setVisibility(View.GONE);
                                    btnCancelVoiceLock.setVisibility(View.VISIBLE);

                                    btnRecordVoice.animate().translationY(0).translationX(0).scaleX(1f).scaleY(1f).setDuration(200).start();
                                    btnRecordVoice.setImageResource(android.R.drawable.ic_menu_send);
                                    btnRecordVoice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#3390EC")));
                                    btnRecordVoice.setColorFilter(Color.WHITE);
                                } else if (dy > 0) {
                                    btnRecordVoice.setTranslationY(-dy);
                                }
                            } else {
                                if (dx > 150) {
                                    isCancelled = true;
                                    cancelRecording();
                                } else if (dx > 0) {
                                    btnRecordVoice.setTranslationX(-dx);
                                    tvSlideToCancel.setAlpha(1 - (dx / 150f));
                                }
                            }
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isRecordingLocked) {
                            btnRecordVoice.animate().scaleX(1f).scaleY(1f).setDuration(100).start();

                            if (!lockedInThisGesture) {
                                // Если это БЫЛ ТАП по уже залоченной кнопке - ОТПРАВЛЯЕМ
                                stopRecordingAndSend();
                            } else {
                                // ВОТ ОНО ИСПРАВЛЕНИЕ!
                                // Мы только что отпустили палец после свайпа вверх.
                                // Сбрасываем флаг, чтобы следующий тап сработал как отправка!
                                lockedInThisGesture = false;
                            }
                            return true;
                        }

                        if (isCancelled) break;
                        stopRecordingAndSend();
                        break;
                }
                return true;
            }
        });
    }

    private void startRecording() {
        isRecording = true;
        audioFilePath = getExternalCacheDir().getAbsolutePath() + "/voice_" + System.currentTimeMillis() + ".m4a";
        typingRef.child(currentUserId).setValue("recording");

        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setOutputFile(audioFilePath);

        try {
            mediaRecorder.prepare();
            mediaRecorder.start();
            recordStartTime = System.currentTimeMillis();
            timerHandler.post(updateTimerRunnable);

            AlphaAnimation blink = new AlphaAnimation(1.0f, 0.2f);
            blink.setDuration(500);
            blink.setRepeatMode(Animation.REVERSE);
            blink.setRepeatCount(Animation.INFINITE);
            redDot.startAnimation(blink);
        } catch (Exception e) {
            e.printStackTrace();
            cancelRecording();
            Toast.makeText(this, "Ошибка микрофона", Toast.LENGTH_SHORT).show();
        }
    }

    private void cancelRecording() {
        resetRecordingUI();
        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception e) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }
        if (audioFilePath != null) new File(audioFilePath).delete();
    }

    private void stopRecordingAndSend() {
        long duration = System.currentTimeMillis() - recordStartTime;
        resetRecordingUI();

        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch (Exception e) {}
            mediaRecorder.release();
            mediaRecorder = null;
        }

        if (duration < 1000) {
            if (audioFilePath != null) new File(audioFilePath).delete();
            Toast.makeText(this, "Слишком короткое", Toast.LENGTH_SHORT).show();
            return;
        }
        uploadAudioAndSend(Uri.fromFile(new File(audioFilePath)), duration);
    }

    private void resetRecordingUI() {
        isRecording = false;
        isRecordingLocked = false;
        typingRef.child(currentUserId).setValue("false");
        timerHandler.removeCallbacks(updateTimerRunnable);
        redDot.clearAnimation();

        // Возвращаем видимость контейнерам
        recordingContainer.setVisibility(View.GONE);
        textInputContainer.setVisibility(View.VISIBLE);
        lockOverlay.setVisibility(View.GONE);
        btnCancelVoiceLock.setVisibility(View.GONE);

        // Возвращаем микрофон в исходный вид
        btnRecordVoice.animate().scaleX(1f).scaleY(1f).translationY(0).translationX(0).setDuration(200).start();
        btnRecordVoice.setImageResource(android.R.drawable.ic_btn_speak_now);
        btnRecordVoice.setBackgroundTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#1A3390EC")));
        btnRecordVoice.setColorFilter(Color.parseColor("#3390EC"));

        tvSlideToCancel.setText("◀ Отменить");
        tvSlideToCancel.setAlpha(1f);

        updateInputUI();
    }

    private Runnable updateTimerRunnable = new Runnable() {
        @Override
        public void run() {
            long millis = System.currentTimeMillis() - recordStartTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            seconds = seconds % 60;
            tvRecordTime.setText(String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds));
            timerHandler.postDelayed(this, 500);
        }
    };

    private void uploadAudioAndSend(Uri audioUri, long durationMs) {
        String tempMessageId = "temp_voice_" + System.currentTimeMillis();
        ChatMessage tempMsg = new ChatMessage(currentUserId, targetUserId, audioUri.toString(), null, System.currentTimeMillis(), "voice");
        tempMsg.setMessageId(tempMessageId);
        attachReplyDataToMessage(tempMsg);

        messageList.add(tempMsg);
        chatAdapter.addUploadingMessage(tempMessageId);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1);

        java.util.concurrent.Future<?> uploadTask = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(audioUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "video");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String secureUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    uploadTasks.remove(tempMessageId);
                    removeTempMessageLocally(tempMessageId);
                    if (!isFinishing() && !isDestroyed()) {
                        String finalId = chatRef.push().getKey();
                        if (finalId != null) {
                            ChatMessage message = new ChatMessage(currentUserId, targetUserId, secureUrl, null, System.currentTimeMillis(), "voice");
                            message.setMessageId(finalId);

                            if (replyingToMessage != null) {
                                message.setReplyMessageId(replyingToMessage.getMessageId());
                                message.setReplySenderId(replyingToMessage.getSenderId());
                                message.setReplyText(replyingToMessage.getText() != null ? replyingToMessage.getText() : "Вложение");
                            }

                            chatRef.child(finalId).setValue(message);
                            closeReplyPreview();
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    uploadTasks.remove(tempMessageId);
                    removeTempMessageLocally(tempMessageId);
                    Toast.makeText(ChatActivity.this, "Ошибка отправки ГС", Toast.LENGTH_SHORT).show();
                });
            }
        });
        uploadTasks.put(tempMessageId, uploadTask);
    }

    /* |-----------------------------------------------------------------------|
     * |                       ЛОГИКА ОТПРАВКИ И ПРЕВЬЮ                    |
     * |-----------------------------------------------------------------------| */

    private void updateInputUI() {
        boolean hasText = etMessageInput.getText().toString().trim().length() > 0;
        boolean hasImage = selectedImageUri != null;

        // Убрали проверку isRecordingLocked отсюда!
        if (hasText || hasImage) {
            btnSend.setVisibility(View.VISIBLE);
            btnRecordVoice.setVisibility(View.GONE);
            btnAttach.setVisibility(View.GONE);
        } else {
            btnAttach.setVisibility(View.VISIBLE);
            btnSend.setVisibility(View.GONE);
            btnRecordVoice.setVisibility(View.VISIBLE); // Микрофон ВСЕГДА видим, если нет текста
        }
    }

    private void closeReplyPreview() {
        replyingToMessage = null;
        editingMessageId = null;
        replyPreviewContainer.setVisibility(View.GONE);
        etMessageInput.setText("");
        updateInputUI();
    }

    private void setupReplyPreview(ChatMessage message, boolean isEditing) {
        VibratorHelper.vibrate(this, 30);
        replyingToMessage = message;
        replyPreviewContainer.setVisibility(View.VISIBLE);

        if (!isEditing) editingMessageId = null;

        String sender = message.getSenderId().equals(currentUserId) ? "Вы" : tvChatUsername.getText().toString();
        tvReplySender.setText(isEditing ? "Редактирование" : sender);

        String previewText = "";
        if ("text".equals(message.getType())) previewText = message.getText();
        else if ("image".equals(message.getType())) previewText = "📷 Фотография";
        else if ("voice".equals(message.getType())) previewText = "🎤 Голосовое сообщение";
        else if ("post".equals(message.getType())) previewText = "🗺️ Воспоминание";
        tvReplyText.setText(previewText);

        etMessageInput.requestFocus();
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.showSoftInput(etMessageInput, InputMethodManager.SHOW_IMPLICIT);
        updateInputUI();
    }

    private void attachReplyDataToMessage(ChatMessage message) {
        if (replyingToMessage != null) {
            message.setReplyMessageId(replyingToMessage.getMessageId());
            message.setReplySenderId(replyingToMessage.getSenderId());
            String replyTxt = "";
            if ("text".equals(replyingToMessage.getType())) replyTxt = replyingToMessage.getText();
            else if ("image".equals(replyingToMessage.getType())) replyTxt = "📷 Фотография";
            else if ("voice".equals(replyingToMessage.getType())) replyTxt = "🎤 Голосовое сообщение";
            else if ("post".equals(message.getType())) replyTxt = "🗺️ Воспоминание";
            message.setReplyText(replyTxt);
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

    private void uploadImageToCloudinaryAndSend(Uri imageUri, String caption) {
        String tempMessageId = "temp_" + System.currentTimeMillis();
        ChatMessage tempMsg = new ChatMessage(currentUserId, targetUserId, imageUri.toString(), caption, System.currentTimeMillis(), "image");
        tempMsg.setMessageId(tempMessageId);
        attachReplyDataToMessage(tempMsg);

        messageList.add(tempMsg);
        chatAdapter.addUploadingMessage(tempMessageId);
        chatAdapter.notifyItemInserted(messageList.size() - 1);
        chatRecyclerView.scrollToPosition(messageList.size() - 1);

        java.util.concurrent.Future<?> uploadTask = Executors.newSingleThreadExecutor().submit(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String secureUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    uploadTasks.remove(tempMessageId);
                    removeTempMessageLocally(tempMessageId);
                    if (!isFinishing() && !isDestroyed()) {
                        String messageId = chatRef.push().getKey();
                        if (messageId != null) {
                            ChatMessage message = new ChatMessage(currentUserId, targetUserId, secureUrl, caption, System.currentTimeMillis(), "image");
                            message.setMessageId(messageId);

                            if (replyingToMessage != null) {
                                message.setReplyMessageId(replyingToMessage.getMessageId());
                                message.setReplySenderId(replyingToMessage.getSenderId());
                                message.setReplyText(replyingToMessage.getText() != null ? replyingToMessage.getText() : "Вложение");
                            }

                            chatRef.child(messageId).setValue(message);
                            closeReplyPreview();
                        }
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    uploadTasks.remove(tempMessageId);
                    removeTempMessageLocally(tempMessageId);
                    Toast.makeText(ChatActivity.this, "Отправка фото прервана", Toast.LENGTH_SHORT).show();
                });
            }
        });
        uploadTasks.put(tempMessageId, uploadTask);
    }

    private void removeTempMessageLocally(String tempId) {
        chatAdapter.removeUploadingMessage(tempId);
        for(int i = 0; i < messageList.size(); i++) {
            if(messageList.get(i).getMessageId().equals(tempId)) {
                messageList.remove(i);
                chatAdapter.notifyItemRemoved(i);
                break;
            }
        }
    }

    private boolean playNextVoiceMessage(String currentId) {
        if (messageList == null || messageList.isEmpty()) return false;

        int currentIndex = -1;
        String senderId = null;

        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId() != null && messageList.get(i).getMessageId().equals(currentId)) {
                currentIndex = i;
                senderId = messageList.get(i).getSenderId();
                break;
            }
        }

        if (currentIndex != -1 && senderId != null) {
            for (int i = currentIndex + 1; i < messageList.size(); i++) {
                ChatMessage nextMsg = messageList.get(i);

                if ("voice".equals(nextMsg.getType())) {
                    if (senderId.equals(nextMsg.getSenderId())) {
                        String nextId = nextMsg.getMessageId();
                        String nextUrl = nextMsg.getImageUrl();

                        int finalI = i;
                        new Handler(getMainLooper()).postDelayed(() -> {
                            chatRecyclerView.smoothScrollToPosition(finalI);
                            AudioPlayerManager.getInstance().play(nextId, nextUrl);
                        }, 500);
                        return true; // Нашли и запустили!
                    }
                    break;
                }
            }
        }
        return false; // Следующего ГС нет
    }

    private void scrollToAndHighlightMessage(String messageId) {
        for (int i = 0; i < messageList.size(); i++) {
            if (messageList.get(i).getMessageId() != null && messageList.get(i).getMessageId().equals(messageId)) {
                androidx.recyclerview.widget.LinearSmoothScroller smoothScroller = new androidx.recyclerview.widget.LinearSmoothScroller(this) {
                    @Override protected int getVerticalSnapPreference() { return androidx.recyclerview.widget.LinearSmoothScroller.SNAP_TO_ANY; }
                    @Override public int calculateDtToFit(int viewStart, int viewEnd, int boxStart, int boxEnd, int snapPreference) {
                        return (boxStart + (boxEnd - boxStart) / 2) - (viewStart + (viewEnd - viewStart) / 2);
                    }
                };
                smoothScroller.setTargetPosition(i);
                if (chatRecyclerView.getLayoutManager() != null) chatRecyclerView.getLayoutManager().startSmoothScroll(smoothScroller);
                new Handler().postDelayed(() -> chatAdapter.highlightMessage(messageId), 400);
                break;
            }
        }
    }

    /* |-----------------------------------------------------------------------|
     * |                           FIREBASE СТАТУСЫ                            |
     * |-----------------------------------------------------------------------| */

    private void loadTargetUserData() {
        statusListener = targetUserRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && !isDestroyed()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String avatarUrl = snapshot.child("profileImageUrl").getValue(String.class);

                    isTargetUserHidden = snapshot.child("privacy").child("hide_online").exists() &&
                            Boolean.TRUE.equals(snapshot.child("privacy").child("hide_online").getValue(Boolean.class));

                    String finalUsername = TextUtils.isEmpty(username) ? "Пользователь" : username;
                    tvChatUsername.setText(finalUsername);
                    if (chatAdapter != null) chatAdapter.setTargetUserName(finalUsername);

                    if (avatarUrl != null && !avatarUrl.isEmpty()) {
                        Glide.with(ChatActivity.this).load(avatarUrl).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(ivChatAvatar);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        typingListener = typingRef.child(targetUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String state = snapshot.getValue(String.class);
                if ("typing".equals(state)) {
                    tvChatStatus.setText("печатает...");
                    tvChatStatus.setTextColor(Color.parseColor("#3390EC"));
                } else if ("recording".equals(state)) {
                    tvChatStatus.setText("записывает голосовое...");
                    tvChatStatus.setTextColor(Color.parseColor("#3390EC"));
                } else {
                    targetUserRef.child("status").addListenerForSingleValueEvent(new ValueEventListener() {
                        @Override
                        public void onDataChange(@NonNull DataSnapshot statSnap) {
                            Object statusObj = statSnap.getValue();
                            String statusText = TimeFormatter.formatStatus(statusObj, isTargetUserHidden);
                            tvChatStatus.setText(statusText);
                            if ("в сети".equals(statusText)) tvChatStatus.setTextColor(getResources().getColor(R.color.online_indicator));
                            else tvChatStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                        }
                        @Override public void onCancelled(@NonNull DatabaseError error) {}
                    });
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
                                    String text = "text".equals(msg.getType()) ? msg.getText() : ("image".equals(msg.getType()) ? "📷 Фотография" : ("voice".equals(msg.getType()) ? "🎤 Голосовое" : "🗺️ Воспоминание"));
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




    /* |-----------------------------------------------------------------------|
     * |                           СВАЙП ЗАКРЫТИЯ                              |
     * |-----------------------------------------------------------------------| */

    @Override
    public boolean dispatchTouchEvent(android.view.MotionEvent ev) {
        if (rootLayout == null) return super.dispatchTouchEvent(ev);

        // Блокируем свайп закрытия, если включен режим выделения сообщений
        if (chatAdapter != null && !chatAdapter.getSelectedMessageIds().isEmpty()) {
            return super.dispatchTouchEvent(ev);
        }
        // Блокируем свайп закрытия, если открыто фото
        if (isGalleryOpen) {
            return super.dispatchTouchEvent(ev);
        }


        switch (ev.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                startX = ev.getRawX();
                startY = ev.getRawY();
                isSwipingToClose = false;
                // Свайп работает от левого края (захватываем 15% ширины экрана, как в ТГ)
                canSwipeBack = startX < (screenWidth * 0.95f);
                break;

            case android.view.MotionEvent.ACTION_MOVE:
                if (!canSwipeBack) break;
                float dx = ev.getRawX() - startX;
                float dy = ev.getRawY() - startY;

                // Используем системный touchSlop вместо жестких 40px
                if (!isSwipingToClose && dx > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.5f) {
                    isSwipingToClose = true;
                    // Прячем клавиатуру при начале свайпа
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null && getCurrentFocus() != null) imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);

                    // Отменяем передачу касаний дочерним элементам
                    android.view.MotionEvent cancelEvent = android.view.MotionEvent.obtain(ev);
                    cancelEvent.setAction(android.view.MotionEvent.ACTION_CANCEL);
                    super.dispatchTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }

                if (isSwipingToClose) {
                    rootLayout.setTranslationX(Math.max(0, dx));
                    return true; // Перехватываем свайп на себя
                }
                break;

            case android.view.MotionEvent.ACTION_UP:
            case android.view.MotionEvent.ACTION_CANCEL:
                if (isSwipingToClose) {
                    float dxUp = ev.getRawX() - startX;
                    if (dxUp > screenWidth / 3f) {
                        // АНИМАЦИЯ ЗАКРЫТИЯ: Уводим окно вправо и закрываем активити
                        rootLayout.animate().translationX(screenWidth).setDuration(200).withEndAction(() -> {
                            finish();
                            overridePendingTransition(0, 0); // Отключаем стандартную системную анимацию
                        }).start();
                    } else {
                        // Возвращаем окно обратно, если свайпнули недостаточно
                        rootLayout.animate().translationX(0).setDuration(200).start();
                    }
                    isSwipingToClose = false;
                    return true;
                }
                break;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onBackPressed() {
        // 1. Если открыта галерея - закрываем её
        if (isGalleryOpen) {
            closeGallery();
            return;
        }

        // 2. Если есть выделенные сообщения - снимаем выделение
        if (chatAdapter != null && !chatAdapter.getSelectedMessageIds().isEmpty()) {
            chatAdapter.clearSelection();
            return;
        }

        // 3. Иначе стандартный выход
        getOnBackPressedDispatcher().onBackPressed();
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
        clearNotification();

        updateMyStatus("online");
    }

    @Override
    protected void onPause() {
        super.onPause();
        currentChatUserId = null;
        updateMyStatus(System.currentTimeMillis());
    }


    private void clearNotification() {
        if (targetUserId != null) {
            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (notificationManager != null) {
                // Удаляем уведомление по тому же ID (hashCode), с которым мы его создавали
                notificationManager.cancel(targetUserId.hashCode());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        AudioPlayerManager.getInstance().stop();
        typingRef.child(currentUserId).setValue("false");
        for (java.util.concurrent.Future<?> task : uploadTasks.values()) {
            if (task != null) task.cancel(true);
        }
        if (targetUserRef != null && statusListener != null) targetUserRef.removeEventListener(statusListener);
        if (chatRef != null && messagesListener != null) chatRef.removeEventListener(messagesListener);
        if (pinnedRef != null && pinnedListener != null) pinnedRef.removeEventListener(pinnedListener);
        if (typingRef != null && typingListener != null) typingRef.child(targetUserId).removeEventListener(typingListener);

        if (mediaRecorder != null) {
            try { mediaRecorder.stop(); } catch(Exception e){}
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }
}
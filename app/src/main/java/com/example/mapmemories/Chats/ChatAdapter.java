package com.example.mapmemories.Chats;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.mapmemories.Post.ViewPostDetailsActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.AudioPlayerManager;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/* |-----------------------------------------------------------------------|
 * |                            АДАПТЕР ЧАТА                               |
 * |-----------------------------------------------------------------------| */
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private Context context;
    private List<ChatMessage> messages;
    private String currentUserId;
    private String targetUserName = "Собеседник";
    private ChatActionListener actionListener;
    private Map<String, PostCacheData> postCache = new HashMap<>();

    private String highlightedMessageId = null;
    private final Handler highlightHandler = new Handler();

    private Set<String> selectedMessageIds = new HashSet<>();
    private Set<String> uploadingMessageIds = new HashSet<>();
    private boolean isSelectionMode = false;

    // Кэш для запоминания длительности голосовых сообщений
    private Map<String, Integer> voiceDurations = new HashMap<>();

    public interface ChatActionListener {
        void onEditMessage(ChatMessage message);
        void onDeleteMessage(ChatMessage message, boolean forEveryone);
        void onReplyMessage(ChatMessage message);
        void onQuoteClicked(String messageId);
        void onMessageHighlighted(String messageId);
        void onPinMessage(ChatMessage message);
        void onReactionSelected(ChatMessage message, String reaction);
        void onSelectionChanged(int selectedCount);
        void onCancelUpload(String messageId);
        void onImageClicked(View thumbView, ChatMessage message);
    }

    public ChatAdapter(Context context, List<ChatMessage> messages, ChatActionListener listener) {
        this.context = context;
        this.messages = messages;
        this.actionListener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
    }

    public void setTargetUserName(String name) {
        if (name != null && !name.isEmpty()) {
            this.targetUserName = name;
            notifyDataSetChanged();
        }
    }

    public void addUploadingMessage(String id) { uploadingMessageIds.add(id); }
    public void removeUploadingMessage(String id) { uploadingMessageIds.remove(id); }

    public void highlightMessage(String messageId) {
        this.highlightedMessageId = messageId;
        notifyDataSetChanged();
        highlightHandler.postDelayed(() -> {
            if (messageId.equals(highlightedMessageId)) {
                highlightedMessageId = null;
                notifyDataSetChanged();
            }
        }, 2000);
        if (actionListener != null) actionListener.onMessageHighlighted(messageId);
    }

    public void clearSelection() {
        Set<String> previousIds = new HashSet<>(selectedMessageIds);
        selectedMessageIds.clear();
        isSelectionMode = false;
        for (String id : previousIds) {
            int pos = getPositionById(id);
            if (pos != -1) notifyItemChanged(pos);
        }
        if (actionListener != null) actionListener.onSelectionChanged(0);
    }

    public Set<String> getSelectedMessageIds() { return selectedMessageIds; }

    private void toggleSelection(String messageId, int position) {
        if (selectedMessageIds.contains(messageId)) selectedMessageIds.remove(messageId);
        else selectedMessageIds.add(messageId);
        if (selectedMessageIds.isEmpty()) isSelectionMode = false;
        notifyItemChanged(position);
        if (actionListener != null) actionListener.onSelectionChanged(selectedMessageIds.size());
    }

    private int getPositionById(String id) {
        if (id == null) return -1;
        for (int i = 0; i < messages.size(); i++) {
            if (id.equals(messages.get(i).getMessageId())) return i;
        }
        return -1;
    }

    /* |-----------------------------------------------------------------------|
     * |                       КОЛЛБЭКИ АУДИОПЛЕЕРА                            |
     * |-----------------------------------------------------------------------| */
    public void updateAudioState(String messageId, boolean isPlaying) {
        int pos = getPositionById(messageId);
        if (pos != -1) notifyItemChanged(pos, "PLAY_STATE_ONLY");
    }

    public void updateAudioProgress(String messageId, int current, int max) {
        int pos = getPositionById(messageId);
        if (pos != -1) notifyItemChanged(pos, new AudioProgress(current, max));
    }

    /* |-----------------------------------------------------------------------|
     * |                           БИНДИНГ UI                                  |
     * |-----------------------------------------------------------------------| */
    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ViewHolder(LayoutInflater.from(context).inflate(R.layout.item_chat_post, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads);
        } else {
            ChatMessage message = messages.get(position);
            for (Object payload : payloads) {
                if (payload instanceof String && payload.equals("PLAY_STATE_ONLY")) {
                    boolean isPlaying = AudioPlayerManager.getInstance().isPlaying() && message.getMessageId().equals(AudioPlayerManager.getInstance().getCurrentPlayingId());
                    holder.btnPlayPause.setImageResource(isPlaying ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);
                } else if (payload instanceof AudioProgress) {
                    AudioProgress p = (AudioProgress) payload;

                    // ИСПРАВЛЕНИЕ: Запоминаем максимальную длительность ГС в кэш
                    voiceDurations.put(message.getMessageId(), p.max);
                    holder.tvVoiceDuration.setText(formatVoiceTime(p.max));
                }
            }
        }
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean isMine = message.getSenderId().equals(currentUserId);
        boolean isHighlighted = message.getMessageId() != null && message.getMessageId().equals(highlightedMessageId);
        boolean isSelected = message.getMessageId() != null && selectedMessageIds.contains(message.getMessageId());
        boolean isUploading = message.getMessageId() != null && uploadingMessageIds.contains(message.getMessageId());

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.contentLayout.getLayoutParams();

        // 1. ВЫДЕЛЕНИЕ
        if (isSelected) {
            holder.itemView.setBackgroundColor(Color.parseColor("#1AE27950"));
            holder.contentLayout.setScaleX(0.92f); holder.contentLayout.setScaleY(0.92f); holder.contentLayout.setAlpha(0.8f);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            holder.contentLayout.setScaleX(1f); holder.contentLayout.setScaleY(1f); holder.contentLayout.setAlpha(1f);
        }

        // 2. СВОЕ / ЧУЖОЕ
        if (isMine) {
            params.horizontalBias = 1.0f;
            holder.contentLayout.setBackgroundResource(R.drawable.bg_msg_mine);
            holder.tvTextMessage.setTextColor(Color.WHITE);
            holder.timeText.setTextColor(Color.WHITE); holder.timeText.setAlpha(0.7f);
            holder.tvQuotedSender.setTextColor(Color.WHITE);
            holder.tvQuotedText.setTextColor(Color.WHITE); holder.tvQuotedText.setAlpha(0.8f);
            holder.itemView.findViewById(R.id.quoteLine).setBackgroundColor(Color.WHITE);

            if (isUploading) holder.ivReadStatus.setVisibility(View.GONE);
            else {
                holder.ivReadStatus.setVisibility(View.VISIBLE);
                holder.ivReadStatus.setImageResource(message.isRead() ? R.drawable.ic_check_double : R.drawable.ic_check);
                holder.ivReadStatus.setColorFilter(message.isRead() ? Color.parseColor("#4FC3F7") : Color.WHITE);
            }
        } else {
            params.horizontalBias = 0.0f;
            holder.contentLayout.setBackgroundResource(R.drawable.bg_msg_theirs);
            int otherColor = ContextCompat.getColor(context, R.color.chat_other_text);
            holder.tvTextMessage.setTextColor(otherColor);
            holder.timeText.setTextColor(otherColor); holder.timeText.setAlpha(0.6f);
            holder.tvQuotedSender.setTextColor(ContextCompat.getColor(context, R.color.accent_coral));
            holder.tvQuotedText.setTextColor(otherColor); holder.tvQuotedText.setAlpha(0.8f);
            holder.itemView.findViewById(R.id.quoteLine).setBackgroundColor(ContextCompat.getColor(context, R.color.accent_coral));
            holder.ivReadStatus.setVisibility(View.GONE);
        }
        holder.contentLayout.setLayoutParams(params);

        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(message.getTimestamp()));

        resetViewHolders(holder);

        if (isHighlighted) {
            holder.highlightOverlay.setVisibility(View.VISIBLE);
            AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
            fadeIn.setDuration(300);
            holder.highlightOverlay.startAnimation(fadeIn);
        }

        // 3. ЦИТАТА
        if (message.getReplyMessageId() != null && !message.getReplyMessageId().isEmpty()) {
            holder.replyQuotedLayout.setVisibility(View.VISIBLE);
            holder.tvQuotedSender.setText(message.getReplySenderId().equals(currentUserId) ? "Вы" : targetUserName);

            String replyText = message.getReplyText();
            if (replyText == null || replyText.isEmpty()) {
                if ("voice".equals(message.getType())) replyText = "🎤 Голосовое сообщение";
                else if ("image".equals(message.getType())) replyText = "📷 Фотография";
                else replyText = "Вложение";
            }
            holder.tvQuotedText.setText(replyText);
            holder.replyQuotedLayout.setOnClickListener(v -> { if (actionListener != null) actionListener.onQuoteClicked(message.getReplyMessageId()); });
        }

        /* |-----------------------------------------------------------------------|
         * |                           ТИПЫ СООБЩЕНИЙ                              |
         * |-----------------------------------------------------------------------| */

        if ("text".equals(message.getType())) {
            holder.tvTextMessage.setVisibility(View.VISIBLE);
            holder.tvTextMessage.setText(message.getText());
        }

        else if ("image".equals(message.getType())) {
            holder.imageContainer.setVisibility(View.VISIBLE);
            String currentUrl = (String) holder.chatAttachedImage.getTag();
            if (currentUrl == null || !currentUrl.equals(message.getImageUrl())) {
                Glide.with(context).load(message.getImageUrl()).dontAnimate().into(holder.chatAttachedImage);
                holder.chatAttachedImage.setTag(message.getImageUrl());
            }
            if (message.getText() != null && !message.getText().isEmpty()) {
                holder.tvTextMessage.setVisibility(View.VISIBLE);
                holder.tvTextMessage.setText(message.getText());
            }

            if (isUploading) {
                holder.uploadOverlay.setVisibility(View.VISIBLE);
                holder.btnCancelUpload.setOnClickListener(v -> actionListener.onCancelUpload(message.getMessageId()));
            } else {
                holder.imageContainer.setOnClickListener(v -> {
                    if (isSelectionMode) {
                        toggleSelection(message.getMessageId(), holder.getAdapterPosition());
                    } else {
                        // Передаем саму картинку (для анимации) и сообщение
                        actionListener.onImageClicked(holder.chatAttachedImage, message);
                    }
                });
            }
        }


        else if ("post".equals(message.getType())) {
            holder.postLayout.setVisibility(View.VISIBLE);
            loadPostDataOptimized(message.getPostId(), holder);
            holder.postLayout.setOnClickListener(v -> {
                if (isSelectionMode) toggleSelection(message.getMessageId(), holder.getAdapterPosition());
                else {
                    Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                    intent.putExtra("postId", message.getPostId());
                    context.startActivity(intent);
                }
            });
        } else if ("voice".equals(message.getType())) {
            holder.voiceLayout.setVisibility(View.VISIBLE);

            int tintColor = isMine ? Color.WHITE : Color.parseColor("#3390EC");
            int bgTintColor = isMine ? Color.parseColor("#33FFFFFF") : Color.parseColor("#1A3390EC");

            holder.btnPlayPause.setColorFilter(tintColor);
            holder.btnPlayPause.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgTintColor));
            holder.tvVoiceDuration.setTextColor(tintColor);

            if (isUploading) {
                holder.tvVoiceDuration.setText("Отправка...");
                holder.btnPlayPause.setImageResource(android.R.drawable.ic_menu_upload);
                holder.btnPlayPause.setOnClickListener(v -> actionListener.onCancelUpload(message.getMessageId()));

                holder.tvVoiceDuration.setOnClickListener(null);
                holder.voiceLayout.setOnClickListener(null);
            } else {
                boolean isThisPlaying = message.getMessageId().equals(AudioPlayerManager.getInstance().getCurrentPlayingId());

                holder.btnPlayPause.setImageResource(isThisPlaying && AudioPlayerManager.getInstance().isPlaying() ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

                // ИСПРАВЛЕНИЕ: Проверяем, знаем ли мы уже время этого ГС из кэша
                if (voiceDurations.containsKey(message.getMessageId())) {
                    // Если знаем - пишем красивое время (например 0:15)
                    holder.tvVoiceDuration.setText(formatVoiceTime(voiceDurations.get(message.getMessageId())));
                } else {
                    // Если еще ни разу не нажимали Play - пишем Аудио
                    holder.tvVoiceDuration.setText("▶ Аудио");
                }

                View.OnClickListener playPauseListener = v -> {
                    if (isSelectionMode) {
                        toggleSelection(message.getMessageId(), holder.getAdapterPosition());
                        return;
                    }

                    // Сообщаем ChatActivity, ставим мы на паузу или включаем
                    if (context instanceof ChatActivity) {
                        ((ChatActivity) context).isAudioManuallyPaused = AudioPlayerManager.getInstance().isPlaying();
                    }

                    if (isThisPlaying) {
                        if (AudioPlayerManager.getInstance().isPlaying()) {
                            AudioPlayerManager.getInstance().pause();
                            holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_play);
                        } else {
                            AudioPlayerManager.getInstance().play(message.getMessageId(), message.getImageUrl());
                            holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                        }
                    } else {
                        AudioPlayerManager.getInstance().play(message.getMessageId(), message.getImageUrl());
                        holder.btnPlayPause.setImageResource(android.R.drawable.ic_media_pause);
                    }
                };

                holder.btnPlayPause.setOnClickListener(playPauseListener);
                holder.tvVoiceDuration.setOnClickListener(playPauseListener);
                holder.voiceLayout.setOnClickListener(playPauseListener);
            }
        }



        // РЕАКЦИИ
        if (message.getReaction() != null && !message.getReaction().isEmpty()) {
            holder.tvReactionBadge.setVisibility(View.VISIBLE);
            holder.tvReactionBadge.setText(message.getReaction());

            // Удаление реакции по клику на нее
            holder.tvReactionBadge.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onReactionSelected(message, null);
            });
        } else {
            holder.tvReactionBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnLongClickListener(v -> {
            if (!isSelectionMode && message.getMessageId() != null && !isUploading) {
                isSelectionMode = true;
                toggleSelection(message.getMessageId(), holder.getAdapterPosition());
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                return true;
            }
            return false;
        });

        holder.itemView.setOnClickListener(v -> {
            if (isUploading) return;
            if (isSelectionMode && message.getMessageId() != null) toggleSelection(message.getMessageId(), holder.getAdapterPosition());
            else showTelegramStyleMenu(message, isMine, holder.contentLayout);
        });
    }

//    private void showImageDialog(String imageUrl) {
//        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
//        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
//        dialog.setContentView(R.layout.dialog_view_avatar); // Создадим ниже программно или используй свой
//        dialog.setContentView(R.layout.dialog_view_avatar);
//
//        ImageView fullImage = new ImageView(context);
//        fullImage.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
//        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
//        Glide.with(context).load(imageUrl).into(fullImage);
//
//        ImageButton btnDownload = new ImageButton(context);
//        btnDownload.setImageResource(android.R.drawable.ic_menu_save);
//        btnDownload.setBackgroundColor(Color.TRANSPARENT);
//        btnDownload.setPadding(32, 32, 32, 32);
//
//        ConstraintLayout layout = new ConstraintLayout(context);
//        layout.addView(fullImage);
//        layout.addView(btnDownload);
//
//        ConstraintLayout.LayoutParams btnParams = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
//        btnParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
//        btnParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
//        btnParams.setMargins(0, 0, 32, 32);
//        btnDownload.setLayoutParams(btnParams);
//
//        dialog.setContentView(layout);
//
//        btnDownload.setOnClickListener(v -> {
//            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
//            Uri uri = Uri.parse(imageUrl);
//            DownloadManager.Request request = new DownloadManager.Request(uri);
//            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
//            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "MapMemories_" + System.currentTimeMillis() + ".jpg");
//            downloadManager.enqueue(request);
//            Toast.makeText(context, "Скачивание началось", Toast.LENGTH_SHORT).show();
//            dialog.dismiss();
//        });
//
//        dialog.show();
//    }

    private void resetViewHolders(ViewHolder holder) {
        holder.tvTextMessage.setVisibility(View.GONE);
        holder.postLayout.setVisibility(View.GONE);
        holder.imageContainer.setVisibility(View.GONE);
        holder.uploadOverlay.setVisibility(View.GONE);
        holder.voiceLayout.setVisibility(View.GONE);
        holder.replyQuotedLayout.setVisibility(View.GONE);
        holder.highlightOverlay.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(null);
        holder.itemView.setOnLongClickListener(null);
        holder.tvReactionBadge.setOnClickListener(null);
        holder.chatAttachedImage.setTag(null);
    }

    private String formatVoiceTime(int totalMs) {
        int totSec = totalMs / 1000;
        return String.format(Locale.getDefault(), "%d:%02d", totSec / 60, totSec % 60);
    }

    /* |-----------------------------------------------------------------------|
     * |                           ВСПЛЫВАЮЩЕЕ МЕНЮ                            |
     * |-----------------------------------------------------------------------| */
    private void showTelegramStyleMenu(ChatMessage message, boolean isMine, View anchorView) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = (int) (screenWidth * 0.7f);

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_telegram_menu, null);

        PopupWindow popupWindow = new PopupWindow(popupView, popupWidth, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(24f);

        LinearLayout reactionContainer = popupView.findViewById(R.id.reactionContainer);
        reactionContainer.setPadding(16, 16, 16, 16);
        String[] emojis = {"👍", "👎", "❤️", "🔥", "🥰", "👏", "😂", "😮", "😢", "😡", "🎉", "💩"};

        for (String emoji : emojis) {
            TextView tvEmoji = new TextView(context);
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(28f);
            tvEmoji.setPadding(24, 16, 24, 16);
            tvEmoji.setGravity(Gravity.CENTER);

            if (emoji.equals(message.getReaction())) {
                GradientDrawable bg = new GradientDrawable();
                bg.setShape(GradientDrawable.OVAL);
                bg.setColor(Color.parseColor("#33E27950"));
                tvEmoji.setBackground(bg);
            }

            tvEmoji.setOnTouchListener((v, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN: v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).start(); break;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL: v.animate().scaleX(1f).scaleY(1f).setDuration(100).setInterpolator(new OvershootInterpolator()).start(); break;
                }
                return false;
            });

            tvEmoji.setOnClickListener(v -> {
                popupWindow.dismiss();
                String newReaction = emoji.equals(message.getReaction()) ? null : emoji;
                if (actionListener != null) actionListener.onReactionSelected(message, newReaction);
            });
            reactionContainer.addView(tvEmoji);
        }

        LinearLayout actionsContainer = popupView.findViewById(R.id.actionsContainer);
        actionsContainer.removeAllViews();

        boolean isPost = "post".equals(message.getType());
        boolean isImage = "image".equals(message.getType());
        boolean isVoice = "voice".equals(message.getType());

        addPopupMenuItem(actionsContainer, "↩️ Ответить", v -> { popupWindow.dismiss(); if (actionListener != null) actionListener.onReplyMessage(message); });

        if ("text".equals(message.getType())) {
            addPopupMenuItem(actionsContainer, "📋 Копировать", v -> {
                popupWindow.dismiss();
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Message", message.getText());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(context, "Скопировано", Toast.LENGTH_SHORT).show();
            });
        }

        addPopupMenuItem(actionsContainer, "📌 Закрепить", v -> { popupWindow.dismiss(); if (actionListener != null) actionListener.onPinMessage(message); });

        if (isPost) {
            addPopupMenuItem(actionsContainer, "🗺️ Открыть воспоминание", v -> {
                popupWindow.dismiss();
                Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                intent.putExtra("postId", message.getPostId());
                context.startActivity(intent);
            });
        }

        if (isMine && !isPost && !isImage && !isVoice) {
            addPopupMenuItem(actionsContainer, "✏️ Изменить", v -> { popupWindow.dismiss(); if (actionListener != null) actionListener.onEditMessage(message); });
        }

        addPopupMenuItemCustomColor(actionsContainer, "🗑️ Удалить сообщение", Color.parseColor("#FF5252"), v -> {
            popupWindow.dismiss();
            if (actionListener != null) actionListener.onDeleteMessage(message, isMine);
        });

        popupView.measure(View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        int popupHeight = popupView.getMeasuredHeight();
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);
        int margin = 32;
        int x = isMine ? Math.max(margin, location[0] + anchorView.getWidth() - popupWidth) : Math.min(location[0], screenWidth - popupWidth - margin);
        int y = location[1] - popupHeight - 20;
        if (y < 100) y = location[1] + anchorView.getHeight() + 20;

        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);
    }

    private void addPopupMenuItem(LinearLayout parent, String text, View.OnClickListener listener) {
        addPopupMenuItemCustomColor(parent, text, ContextCompat.getColor(context, R.color.text_primary), listener);
    }

    private void addPopupMenuItemCustomColor(LinearLayout parent, String text, int color, View.OnClickListener listener) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(16f);
        textView.setTextColor(color);
        textView.setPadding(40, 32, 40, 32);

        android.util.TypedValue outValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);

        textView.setOnClickListener(listener);
        parent.addView(textView);
    }

    private void loadPostDataOptimized(String postId, ViewHolder holder) {
        if (postCache.containsKey(postId)) {
            holder.postTitle.setText(postCache.get(postId).title);
            Glide.with(context).load(postCache.get(postId).imageUrl).into(holder.postImage);
            return;
        }
        holder.postTitle.setText("Загрузка...");
        FirebaseDatabase.getInstance().getReference("posts").child(postId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String title = snapshot.child("title").getValue(String.class);
                    String imageUrl = snapshot.child("mediaUrls").exists() ? snapshot.child("mediaUrls").child("0").getValue(String.class) : snapshot.child("mediaUrl").getValue(String.class);
                    postCache.put(postId, new PostCacheData(title != null ? title : "Без названия", imageUrl));
                    holder.postTitle.setText(title);
                    if (imageUrl != null) Glide.with(context).load(imageUrl).into(holder.postImage);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override public int getItemCount() { return messages.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout contentLayout;
        LinearLayout postLayout, replyQuotedLayout, voiceLayout;
        TextView tvTextMessage, postTitle, timeText, tvQuotedSender, tvQuotedText, tvReactionBadge, tvVoiceDuration;
        ImageView postImage, chatAttachedImage, ivReadStatus, btnCancelUpload;
        View highlightOverlay;
        MaterialCardView imageContainer;
        FrameLayout uploadOverlay;
        ImageButton btnPlayPause;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            contentLayout = itemView.findViewById(R.id.contentLayout);
            postLayout = itemView.findViewById(R.id.postLayout);
            tvTextMessage = itemView.findViewById(R.id.tvTextMessage);
            postImage = itemView.findViewById(R.id.postImage);
            postTitle = itemView.findViewById(R.id.postTitle);
            timeText = itemView.findViewById(R.id.msgTime);
            chatAttachedImage = itemView.findViewById(R.id.chatAttachedImage);
            replyQuotedLayout = itemView.findViewById(R.id.replyQuotedLayout);
            tvQuotedSender = itemView.findViewById(R.id.tvQuotedSender);
            tvQuotedText = itemView.findViewById(R.id.tvQuotedText);
            highlightOverlay = itemView.findViewById(R.id.highlightOverlay);
            tvReactionBadge = itemView.findViewById(R.id.tvReactionBadge);
            ivReadStatus = itemView.findViewById(R.id.ivReadStatus);
            imageContainer = itemView.findViewById(R.id.imageContainer);
            uploadOverlay = itemView.findViewById(R.id.uploadOverlay);
            btnCancelUpload = itemView.findViewById(R.id.btnCancelUpload);
            voiceLayout = itemView.findViewById(R.id.voiceLayout);
            btnPlayPause = itemView.findViewById(R.id.btnPlayPause);
            tvVoiceDuration = itemView.findViewById(R.id.tvVoiceDuration);
        }
    }
    private static class PostCacheData {
        String title, imageUrl;
        PostCacheData(String title, String imageUrl) { this.title = title; this.imageUrl = imageUrl; }
    }
    private static class AudioProgress {
        int current, max;
        AudioProgress(int current, int max) { this.current = current; this.max = max; }
    }
}
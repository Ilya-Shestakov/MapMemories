package com.example.mapmemories.Chats;

import android.app.Dialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AlphaAnimation;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private Context context;
    private List<ChatMessage> messages;
    private String currentUserId;
    private String targetUserName = "Собеседник";
    private ChatActionListener actionListener;
    private Map<String, PostCacheData> postCache = new HashMap<>();

    private String highlightedMessageId = null;
    private final Handler highlightHandler = new Handler();
    private static final long HIGHLIGHT_DURATION = 2000;

    // --- ЛОГИКА ВЫДЕЛЕНИЯ ---
    private Set<String> selectedMessageIds = new HashSet<>();
    private boolean isSelectionMode = false;

    public interface ChatActionListener {
        void onEditMessage(ChatMessage message);
        void onDeleteMessage(ChatMessage message, boolean forEveryone);
        void onReplyMessage(ChatMessage message);
        void onQuoteClicked(String messageId);
        void onMessageHighlighted(String messageId);
        void onPinMessage(ChatMessage message);
        void onReactionSelected(ChatMessage message, String reaction);
        // Новый метод для обновления тулбара выделения
        void onSelectionChanged(int selectedCount);
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

    public void highlightMessage(String messageId) {
        this.highlightedMessageId = messageId;
        notifyDataSetChanged();
        highlightHandler.postDelayed(() -> {
            if (messageId.equals(highlightedMessageId)) {
                highlightedMessageId = null;
                notifyDataSetChanged();
            }
        }, HIGHLIGHT_DURATION);
        if (actionListener != null) actionListener.onMessageHighlighted(messageId);
    }

    // --- МЕТОДЫ ДЛЯ РАБОТЫ С ВЫДЕЛЕНИЕМ ---
    public void clearSelection() {
        selectedMessageIds.clear();
        isSelectionMode = false;
        notifyDataSetChanged();
        if (actionListener != null) actionListener.onSelectionChanged(0);
    }

    public Set<String> getSelectedMessageIds() {
        return selectedMessageIds;
    }

    private void toggleSelection(String messageId) {
        if (selectedMessageIds.contains(messageId)) {
            selectedMessageIds.remove(messageId);
        } else {
            selectedMessageIds.add(messageId);
        }

        if (selectedMessageIds.isEmpty()) {
            isSelectionMode = false;
        }

        notifyDataSetChanged();
        if (actionListener != null) actionListener.onSelectionChanged(selectedMessageIds.size());
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        boolean isMine = message.getSenderId().equals(currentUserId);
        boolean isHighlighted = message.getMessageId() != null && message.getMessageId().equals(highlightedMessageId);
        boolean isSelected = message.getMessageId() != null && selectedMessageIds.contains(message.getMessageId());

        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.contentLayout.getLayoutParams();

        // ВИЗУАЛ ВЫДЕЛЕНИЯ
        if (isSelected) {
            // Мягкий цвет выделения (полупрозрачный акцентный цвет)
            holder.itemView.setBackgroundColor(Color.parseColor("#1AE27950"));
            // Слегка уменьшаем пузырь сообщения для тактильного эффекта
            holder.contentLayout.setScaleX(0.92f);
            holder.contentLayout.setScaleY(0.92f);
            holder.contentLayout.setAlpha(0.8f);
        } else {
            holder.itemView.setBackgroundColor(Color.TRANSPARENT);
            // Возвращаем в нормальное состояние
            holder.contentLayout.setScaleX(1f);
            holder.contentLayout.setScaleY(1f);
            holder.contentLayout.setAlpha(1f);
        }

        if (isMine) {
            params.horizontalBias = 1.0f;
            holder.contentLayout.setBackgroundResource(R.drawable.bg_msg_mine);

            int myTextColor = Color.WHITE;
            holder.tvTextMessage.setTextColor(myTextColor);
            holder.timeText.setTextColor(myTextColor);
            holder.timeText.setAlpha(0.7f);

            holder.tvQuotedSender.setTextColor(myTextColor);
            holder.tvQuotedText.setTextColor(myTextColor);
            holder.tvQuotedText.setAlpha(0.8f);
            holder.itemView.findViewById(R.id.quoteLine).setBackgroundColor(myTextColor);
        } else {
            params.horizontalBias = 0.0f;
            holder.contentLayout.setBackgroundResource(R.drawable.bg_msg_theirs);

            int otherTextColor = ContextCompat.getColor(context, R.color.chat_other_text);
            holder.tvTextMessage.setTextColor(otherTextColor);
            holder.timeText.setTextColor(otherTextColor);
            holder.timeText.setAlpha(0.6f);

            holder.tvQuotedSender.setTextColor(ContextCompat.getColor(context, R.color.accent_coral));
            holder.tvQuotedText.setTextColor(otherTextColor);
            holder.tvQuotedText.setAlpha(0.8f);
            holder.itemView.findViewById(R.id.quoteLine).setBackgroundColor(ContextCompat.getColor(context, R.color.accent_coral));
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

        if (message.getReplyMessageId() != null && !message.getReplyMessageId().isEmpty()) {
            holder.replyQuotedLayout.setVisibility(View.VISIBLE);
            String senderName = targetUserName;
            if (message.getReplySenderId() != null && message.getReplySenderId().equals(currentUserId)) senderName = "Вы";
            holder.tvQuotedSender.setText(senderName);
            holder.tvQuotedText.setText(message.getReplyText() != null ? message.getReplyText() : "");
            holder.replyQuotedLayout.setOnClickListener(v -> {
                if (actionListener != null) actionListener.onQuoteClicked(message.getReplyMessageId());
            });
        }

        if ("text".equals(message.getType())) {
            holder.tvTextMessage.setVisibility(View.VISIBLE);
            holder.tvTextMessage.setText(message.getText());
        } else if ("image".equals(message.getType())) {
            holder.chatAttachedImage.setVisibility(View.VISIBLE);
            Glide.with(context).load(message.getImageUrl()).transform(new RoundedCorners(24)).into(holder.chatAttachedImage);
            if (message.getText() != null && !message.getText().isEmpty()) {
                holder.tvTextMessage.setVisibility(View.VISIBLE);
                holder.tvTextMessage.setText(message.getText());
            }
            holder.chatAttachedImage.setOnClickListener(v -> {
                if (isSelectionMode) toggleSelection(message.getMessageId());
                else showImageDialog(message.getImageUrl());
            });
        } else if ("post".equals(message.getType())) {
            holder.postLayout.setVisibility(View.VISIBLE);
            loadPostDataOptimized(message.getPostId(), holder);
            holder.contentLayout.setOnClickListener(v -> {
                if (isSelectionMode) toggleSelection(message.getMessageId());
                else {
                    Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                    intent.putExtra("postId", message.getPostId());
                    context.startActivity(intent);
                }
            });
        }

        if (message.getReaction() != null && !message.getReaction().isEmpty()) {
            holder.tvReactionBadge.setVisibility(View.VISIBLE);
            holder.tvReactionBadge.setText(message.getReaction());
        } else {
            holder.tvReactionBadge.setVisibility(View.GONE);
        }

        // --- ОБРАБОТКА НАЖАТИЙ ДЛЯ ВЫДЕЛЕНИЯ И МЕНЮ ---
        holder.contentLayout.setOnLongClickListener(v -> {
            if (!isSelectionMode && message.getMessageId() != null) {
                isSelectionMode = true;
                toggleSelection(message.getMessageId());
                return true;
            }
            return false;
        });

        holder.contentLayout.setOnClickListener(v -> {
            if (isSelectionMode && message.getMessageId() != null) {
                toggleSelection(message.getMessageId());
            } else {
                showTelegramStyleMenu(message, isMine, holder.contentLayout);
            }
        });
    }

    private void showImageDialog(String imageUrl) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_view_avatar);

        ImageView fullImage = new ImageView(context);
        fullImage.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        fullImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
        Glide.with(context).load(imageUrl).into(fullImage);

        ImageButton btnDownload = new ImageButton(context);
        btnDownload.setImageResource(android.R.drawable.ic_menu_save);
        btnDownload.setBackgroundColor(Color.TRANSPARENT);
        btnDownload.setPadding(32, 32, 32, 32);

        ConstraintLayout layout = new ConstraintLayout(context);
        layout.addView(fullImage);
        layout.addView(btnDownload);

        ConstraintLayout.LayoutParams btnParams = new ConstraintLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        btnParams.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
        btnParams.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID;
        btnParams.setMargins(0, 0, 32, 32);
        btnDownload.setLayoutParams(btnParams);

        dialog.setContentView(layout);

        btnDownload.setOnClickListener(v -> {
            DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
            Uri uri = Uri.parse(imageUrl);
            DownloadManager.Request request = new DownloadManager.Request(uri);
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "MapMemories_" + System.currentTimeMillis() + ".jpg");
            downloadManager.enqueue(request);
            Toast.makeText(context, "Скачивание началось", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });

        dialog.show();
    }

    private void resetViewHolders(ViewHolder holder) {
        holder.tvTextMessage.setVisibility(View.GONE);
        holder.postLayout.setVisibility(View.GONE);
        holder.chatAttachedImage.setVisibility(View.GONE);
        holder.replyQuotedLayout.setVisibility(View.GONE);
        holder.highlightOverlay.setVisibility(View.GONE);
        holder.contentLayout.setOnClickListener(null);
        holder.replyQuotedLayout.setOnClickListener(null);
        holder.chatAttachedImage.setOnClickListener(null);
    }

    private void showTelegramStyleMenu(ChatMessage message, boolean isMine, View anchorView) {
        int screenWidth = context.getResources().getDisplayMetrics().widthPixels;
        int popupWidth = (int) (screenWidth * 0.6f);

        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_telegram_menu, null);

        PopupWindow popupWindow = new PopupWindow(
                popupView,
                popupWidth,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true
        );
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(16f);

        LinearLayout reactionContainer = popupView.findViewById(R.id.reactionContainer);
        String[] emojis = {
                "👍", "👎", "❤️", "🔥", "🥰", "👏",
                "😂", "😮", "😢", "😡", "🎉", "💩"
        };

        for (String emoji : emojis) {
            TextView tvEmoji = new TextView(context);
            tvEmoji.setText(emoji);
            tvEmoji.setTextSize(26f);
            tvEmoji.setPadding(20, 12, 20, 12);

            if (emoji.equals(message.getReaction())) {
                tvEmoji.setBackgroundResource(R.drawable.bg_reaction_badge);
            }

            android.util.TypedValue outValue = new android.util.TypedValue();
            context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, outValue, true);
            tvEmoji.setForeground(ContextCompat.getDrawable(context, outValue.resourceId));

            tvEmoji.setOnClickListener(v -> {
                popupWindow.dismiss();
                String newReaction = emoji.equals(message.getReaction()) ? null : emoji;
                if (actionListener != null) actionListener.onReactionSelected(message, newReaction);
            });
            reactionContainer.addView(tvEmoji);
        }

        LinearLayout actionsContainer = popupView.findViewById(R.id.actionsContainer);
        boolean isPost = "post".equals(message.getType());
        boolean isImage = "image".equals(message.getType());

        addPopupMenuItem(actionsContainer, "↩️ Ответить", v -> {
            popupWindow.dismiss();
            if (actionListener != null) actionListener.onReplyMessage(message);
        });

        if ("text".equals(message.getType())) {
            addPopupMenuItem(actionsContainer, "📋 Копировать", v -> {
                popupWindow.dismiss();
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Message", message.getText());
                clipboard.setPrimaryClip(clip);
            });
        }

        addPopupMenuItem(actionsContainer, "📌 Закрепить", v -> {
            popupWindow.dismiss();
            if (actionListener != null) actionListener.onPinMessage(message);
        });

        if (isPost) {
            addPopupMenuItem(actionsContainer, "🗺️ Открыть воспоминание", v -> {
                popupWindow.dismiss();
                Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                intent.putExtra("postId", message.getPostId());
                context.startActivity(intent);
            });
        }

        if (isMine && !isPost && !isImage) {
            addPopupMenuItem(actionsContainer, "✏️ Изменить", v -> {
                popupWindow.dismiss();
                if (actionListener != null) actionListener.onEditMessage(message);
            });
        }

        addPopupMenuItem(actionsContainer, "🗑️ Удалить у меня", v -> {
            popupWindow.dismiss();
            if (actionListener != null) actionListener.onDeleteMessage(message, false);
        });

        if (isMine) {
            addPopupMenuItem(actionsContainer, "💥 Удалить у всех", v -> {
                popupWindow.dismiss();
                if (actionListener != null) actionListener.onDeleteMessage(message, true);
            });
        }

        popupView.measure(
                View.MeasureSpec.makeMeasureSpec(popupWidth, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.UNSPECIFIED
        );
        int popupHeight = popupView.getMeasuredHeight();

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        int margin = 32;
        int x, y;

        if (isMine) {
            x = location[0] + anchorView.getWidth() - popupWidth;
            x = Math.max(margin, x);
        } else {
            x = location[0];
            x = Math.min(x, screenWidth - popupWidth - margin);
        }

        y = location[1] - popupHeight - 20;
        if (y < 100) {
            y = location[1] + anchorView.getHeight() + 20;
        }

        popupWindow.setAnimationStyle(android.R.style.Animation_Dialog);
        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, x, y);
    }

    private void addPopupMenuItem(LinearLayout parent, String text, View.OnClickListener listener) {
        TextView textView = new TextView(context);
        textView.setText(text);
        textView.setTextSize(16f);
        textView.setTextColor(ContextCompat.getColor(context, R.color.text_primary));
        textView.setPadding(32, 24, 32, 24);

        android.util.TypedValue outValue = new android.util.TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        textView.setBackgroundResource(outValue.resourceId);

        textView.setOnClickListener(listener);
        parent.addView(textView);
    }

    private void loadPostDataOptimized(String postId, ViewHolder holder) {
        if (postCache.containsKey(postId)) {
            PostCacheData data = postCache.get(postId);
            holder.postTitle.setText(data.title);
            if (data.imageUrl != null) Glide.with(context).load(data.imageUrl).transform(new RoundedCorners(24)).into(holder.postImage);
            return;
        }
        holder.postTitle.setText("Загрузка...");
        FirebaseDatabase.getInstance().getReference("posts").child(postId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String title = snapshot.child("title").getValue(String.class);
                    String imageUrl = snapshot.child("mediaUrls").exists() ? snapshot.child("mediaUrls").child("0").getValue(String.class) : snapshot.child("mediaUrl").getValue(String.class);
                    title = title != null ? title : "Без названия";
                    postCache.put(postId, new PostCacheData(title, imageUrl));
                    holder.postTitle.setText(title);
                    if (imageUrl != null) Glide.with(context).load(imageUrl).transform(new RoundedCorners(24)).into(holder.postImage);
                } else {
                    holder.postTitle.setText("Пост удален");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() { return messages.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout contentLayout;
        LinearLayout postLayout, replyQuotedLayout;
        TextView tvTextMessage, postTitle, timeText, tvQuotedSender, tvQuotedText, tvReactionBadge;
        ImageView postImage, chatAttachedImage;
        View highlightOverlay;

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
        }
    }

    private static class PostCacheData {
        String title, imageUrl;
        PostCacheData(String title, String imageUrl) { this.title = title; this.imageUrl = imageUrl; }
    }
}
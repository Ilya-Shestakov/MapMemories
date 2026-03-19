package com.example.mapmemories.Chats;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.example.mapmemories.Post.ViewPostDetailsActivity;
import com.example.mapmemories.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private Context context;
    private List<ChatMessage> messages;
    private String currentUserId;
    private ChatActionListener actionListener;
    private Map<String, PostCacheData> postCache = new HashMap<>();

    public interface ChatActionListener {
        void onEditMessage(ChatMessage message);
        void onDeleteMessage(ChatMessage message, boolean forEveryone);
    }

    public ChatAdapter(Context context, List<ChatMessage> messages, ChatActionListener listener) {
        this.context = context;
        this.messages = messages;
        this.actionListener = listener;
        this.currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
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

        // 1. НАСТРОЙКА ДИЗАЙНА (Линии, прозрачность, выравнивание)
        ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) holder.contentLayout.getLayoutParams();
        holder.tvTextMessage.setTextColor(context.getResources().getColor(R.color.text_primary));

        if (isMine) {
            params.horizontalBias = 1.0f;
            holder.contentLayout.setGravity(Gravity.END);
            holder.tvTextMessage.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_END);

            int primaryColor = context.getResources().getColor(R.color.text_primary);
            holder.lineTop.setBackgroundColor(primaryColor);
            holder.lineBottom.setBackgroundColor(primaryColor);
            holder.lineTop.setAlpha(0.15f);
            holder.lineBottom.setAlpha(0.15f);
        } else {
            params.horizontalBias = 0.0f;
            holder.contentLayout.setGravity(Gravity.START);
            holder.tvTextMessage.setTextAlignment(View.TEXT_ALIGNMENT_VIEW_START);

            int secondaryColor = context.getResources().getColor(R.color.text_secondary);
            holder.lineTop.setBackgroundColor(secondaryColor);
            holder.lineBottom.setBackgroundColor(secondaryColor);
            holder.lineTop.setAlpha(0.15f);
            holder.lineBottom.setAlpha(0.15f);
        }
        holder.contentLayout.setLayoutParams(params);

        // Время
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(message.getTimestamp()));

        // Сброс видимости
        holder.tvTextMessage.setVisibility(View.GONE);
        holder.postLayout.setVisibility(View.GONE);
        holder.chatAttachedImage.setVisibility(View.GONE);
        holder.itemView.setOnClickListener(null);

        // 2. ОБРАБОТКА ТИПОВ СООБЩЕНИЙ
        if ("text".equals(message.getType())) {
            holder.tvTextMessage.setVisibility(View.VISIBLE);
            holder.tvTextMessage.setText(message.getText());

        } else if ("image".equals(message.getType())) {
            holder.chatAttachedImage.setVisibility(View.VISIBLE);
            Glide.with(context).load(message.getImageUrl())
                    .transform(new CenterCrop(), new RoundedCorners(24))
                    .into(holder.chatAttachedImage);

            if (message.getText() != null && !message.getText().isEmpty()) {
                holder.tvTextMessage.setVisibility(View.VISIBLE);
                holder.tvTextMessage.setText(message.getText());
            }

        } else if ("post".equals(message.getType())) {
            holder.postLayout.setVisibility(View.VISIBLE);
            loadPostDataOptimized(message.getPostId(), holder);

            holder.contentLayout.setOnClickListener(v -> {
                Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                intent.putExtra("postId", message.getPostId());
                context.startActivity(intent);
            });
        }

        // 3. МЕНЮ ПО ДОЛГОМУ НАЖАТИЮ (Привязываем к contentLayout - к самому пузырю, а не пустой строке)
        holder.rootMessageLayout.setOnLongClickListener(v -> {
            showContextMenu(message, isMine, v);
            return true;
        });
    }

    private void showContextMenu(ChatMessage message, boolean isMine, View anchorView) {
        View popupView = LayoutInflater.from(context).inflate(R.layout.popup_message_options, null);
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                true // True чтобы окно закрывалось по клику снаружи
        );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(10f);

        TextView btnOpenPost = popupView.findViewById(R.id.btnOpenPost);
        TextView btnEdit = popupView.findViewById(R.id.btnEdit);
        TextView btnDeleteMe = popupView.findViewById(R.id.btnDeleteMe);
        TextView btnDeleteAll = popupView.findViewById(R.id.btnDeleteAll);

        boolean isPost = "post".equals(message.getType());
        boolean isImage = "image".equals(message.getType());

        if (isPost) btnOpenPost.setVisibility(View.VISIBLE);
        if (isMine && !isPost && !isImage) btnEdit.setVisibility(View.VISIBLE);
        if (isMine) btnDeleteAll.setVisibility(View.VISIBLE);

        btnOpenPost.setOnClickListener(v -> {
            popupWindow.dismiss();
            Intent intent = new Intent(context, ViewPostDetailsActivity.class);
            intent.putExtra("postId", message.getPostId());
            context.startActivity(intent);
        });

        btnEdit.setOnClickListener(v -> {
            popupWindow.dismiss();
            actionListener.onEditMessage(message);
        });

        btnDeleteMe.setOnClickListener(v -> {
            popupWindow.dismiss();
            actionListener.onDeleteMessage(message, false);
        });

        btnDeleteAll.setOnClickListener(v -> {
            popupWindow.dismiss();
            actionListener.onDeleteMessage(message, true);
        });

        // Расчет позиции окна относительно пузыря сообщения
        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        // Если мое (справа) - сдвигаем влево, иначе (слева) - сдвигаем вправо
        int xOffset = isMine ? location[0] - 200 : location[0] + anchorView.getWidth() + 20;
        int yOffset = location[1] + (anchorView.getHeight() / 4);

        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, Math.max(0, xOffset), yOffset);

        // Логика плавного следования окна за сообщением при прокрутке чата
        ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (popupWindow.isShowing()) {
                    int[] newLoc = new int[2];
                    anchorView.getLocationOnScreen(newLoc);
                    int newX = isMine ? newLoc[0] - 200 : newLoc[0] + anchorView.getWidth() + 20;
                    popupWindow.update(Math.max(0, newX), newLoc[1] + (anchorView.getHeight() / 4), -1, -1);
                }
                return true;
            }
        };

        anchorView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);
        popupWindow.setOnDismissListener(() -> anchorView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener));
    }

    private void loadPostDataOptimized(String postId, ViewHolder holder) {
        if (postCache.containsKey(postId)) {
            PostCacheData data = postCache.get(postId);
            holder.postTitle.setText(data.title);
            if (data.imageUrl != null) {
                Glide.with(context).load(data.imageUrl).transform(new CenterCrop(), new RoundedCorners(24)).into(holder.postImage);
            }
            return;
        }

        holder.postTitle.setText("Загрузка...");
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);
        postRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String title = snapshot.child("title").getValue(String.class);
                    String imageUrl = null;
                    if (snapshot.child("mediaUrls").exists()) {
                        imageUrl = snapshot.child("mediaUrls").child("0").getValue(String.class);
                    } else {
                        imageUrl = snapshot.child("mediaUrl").getValue(String.class);
                    }

                    title = title != null ? title : "Без названия";
                    postCache.put(postId, new PostCacheData(title, imageUrl));
                    holder.postTitle.setText(title);
                    if (imageUrl != null) {
                        Glide.with(context).load(imageUrl).transform(new CenterCrop(), new RoundedCorners(24)).into(holder.postImage);
                    }
                } else {
                    holder.postTitle.setText("Пост удален");
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout contentLayout, postLayout;
        TextView tvTextMessage, postTitle, timeText;
        ImageView postImage, chatAttachedImage;
        View lineTop, lineBottom;
        ConstraintLayout rootMessageLayout;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rootMessageLayout = itemView.findViewById(R.id.rootMessageLayout);
            contentLayout = itemView.findViewById(R.id.contentLayout);
            postLayout = itemView.findViewById(R.id.postLayout);
            tvTextMessage = itemView.findViewById(R.id.tvTextMessage);
            postImage = itemView.findViewById(R.id.postImage);
            postTitle = itemView.findViewById(R.id.postTitle);
            timeText = itemView.findViewById(R.id.msgTime);
            chatAttachedImage = itemView.findViewById(R.id.chatAttachedImage);
            lineTop = itemView.findViewById(R.id.lineTop);
            lineBottom = itemView.findViewById(R.id.lineBottom);
        }
    }

    private static class PostCacheData {
        String title;
        String imageUrl;
        PostCacheData(String title, String imageUrl) {
            this.title = title;
            this.imageUrl = imageUrl;
        }
    }
}
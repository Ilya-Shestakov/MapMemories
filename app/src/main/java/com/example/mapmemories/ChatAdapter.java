package com.example.mapmemories;

import android.content.Context;
import android.content.Intent;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.graphics.Color;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;

public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {

    private Context context;
    private List<ChatMessage> messages;
    private String currentUserId;
    private ChatActionListener actionListener;

    // Интерфейс для связи с Activity (для редактирования и удаления)
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

        // 1. Настройка выравнивания и фона
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.container.getLayoutParams();
        if (isMine) {
            params.gravity = Gravity.END;
            holder.container.setBackgroundResource(R.drawable.bg_chat_bubble_me);
        } else {
            params.gravity = Gravity.START;
            holder.container.setBackgroundResource(R.drawable.bg_chat_bubble_other);
        }
        holder.container.setLayoutParams(params);

        // 2. Время
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(message.getTimestamp()));

        // 3. ПРОВЕРКА ТИПА СООБЩЕНИЯ
        if ("text".equals(message.getType())) {
            holder.tvTextMessage.setVisibility(View.VISIBLE);
            holder.postLayout.setVisibility(View.GONE);
            holder.tvTextMessage.setText(message.getText());

            // Обычный клик по тексту ничего не делает
            holder.itemView.setOnClickListener(null);
        } else {
            holder.tvTextMessage.setVisibility(View.GONE);
            holder.postLayout.setVisibility(View.VISIBLE);
            loadPostData(message.getPostId(), holder);

            // Клик по посту открывает его
            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, ViewPostDetailsActivity.class);
                intent.putExtra("postId", message.getPostId());
                context.startActivity(intent);
            });
        }

        // 4. ДОЛГОЕ НАЖАТИЕ (МЕНЮ)
        holder.itemView.setOnLongClickListener(v -> {
            showContextMenu(message, isMine);
            return true;
        });
    }

    private void showContextMenu(ChatMessage message, boolean isMine) {
        // Создаем BottomSheet
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(context);
        View view = LayoutInflater.from(context).inflate(R.layout.bottom_sheet_message_options, null);
        bottomSheetDialog.setContentView(view);

        // Делаем фон прозрачным, чтобы было видно наши закругленные углы
        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);

        // Находим кнопки
        TextView btnOpenPost = view.findViewById(R.id.btnOpenPost);
        TextView btnEdit = view.findViewById(R.id.btnEdit);
        TextView btnDeleteMe = view.findViewById(R.id.btnDeleteMe);
        TextView btnDeleteAll = view.findViewById(R.id.btnDeleteAll);

        boolean isPost = "post".equals(message.getType());

        // Настраиваем видимость кнопок
        if (isPost) {
            btnOpenPost.setVisibility(View.VISIBLE);
        }
        if (isMine && !isPost) {
            btnEdit.setVisibility(View.VISIBLE);
        }
        if (isMine) {
            btnDeleteAll.setVisibility(View.VISIBLE);
        }

        // Обработка нажатий
        btnOpenPost.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            Intent intent = new Intent(context, ViewPostDetailsActivity.class);
            intent.putExtra("postId", message.getPostId());
            context.startActivity(intent);
        });

        btnEdit.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            actionListener.onEditMessage(message);
        });

        btnDeleteMe.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            actionListener.onDeleteMessage(message, false);
        });

        btnDeleteAll.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            actionListener.onDeleteMessage(message, true);
        });

        // Показываем меню
        bottomSheetDialog.show();
    }

    private void loadPostData(String postId, ViewHolder holder) {
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);
        postRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String title = snapshot.child("title").getValue(String.class);
                    String imageUrl = snapshot.child("mediaUrl").getValue(String.class);

                    holder.postTitle.setText(title != null ? title : "Без названия");

                    if (imageUrl != null) {
                        // СКРУГЛЯЕМ КАРТИНКУ ВНУТРИ ПОСТА
                        Glide.with(context)
                                .load(imageUrl)
                                .transform(new CenterCrop(), new RoundedCorners(24))
                                .into(holder.postImage);
                    }
                } else {
                    holder.postTitle.setText("Пост удален");
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        LinearLayout container, postLayout;
        TextView tvTextMessage, postTitle, timeText;
        ImageView postImage;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.messageContainer);
            postLayout = itemView.findViewById(R.id.postLayout);
            tvTextMessage = itemView.findViewById(R.id.tvTextMessage);
            postImage = itemView.findViewById(R.id.postImage);
            postTitle = itemView.findViewById(R.id.postTitle);
            timeText = itemView.findViewById(R.id.msgTime);
        }
    }
}
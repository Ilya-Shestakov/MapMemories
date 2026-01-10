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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
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

    public ChatAdapter(Context context, List<ChatMessage> messages) {
        this.context = context;
        this.messages = messages;
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

        // 1. Настройка выравнивания (Свое справа, Чужое слева)
        LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) holder.container.getLayoutParams();
        if (message.getSenderId().equals(currentUserId)) {
            params.gravity = Gravity.END;
            // Можно менять цвет фона контейнера здесь
        } else {
            params.gravity = Gravity.START;
        }
        holder.container.setLayoutParams(params);

        // 2. Время
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
        holder.timeText.setText(sdf.format(message.getTimestamp()));

        // 3. ЗАГРУЗКА ДАННЫХ ПОСТА
        loadPostData(message.getPostId(), holder);

        // 4. Клик по сообщению открывает пост
        holder.itemView.setOnClickListener(v -> {
            Intent intent = new Intent(context, ViewPostDetailsActivity.class); // Или PostDetailsActivity
            intent.putExtra("postId", message.getPostId());
            context.startActivity(intent);
        });
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
                        Glide.with(context)
                                .load(imageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder) // Заглушка
                                .centerCrop()
                                .into(holder.postImage);
                    }
                } else {
                    holder.postTitle.setText("Пост удален");
                    holder.postImage.setImageResource(android.R.drawable.ic_menu_delete);
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
        LinearLayout container;
        ImageView postImage;
        TextView postTitle, timeText;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            container = itemView.findViewById(R.id.messageContainer);
            postImage = itemView.findViewById(R.id.postImage);
            postTitle = itemView.findViewById(R.id.postTitle);
            timeText = itemView.findViewById(R.id.msgTime);
        }
    }
}
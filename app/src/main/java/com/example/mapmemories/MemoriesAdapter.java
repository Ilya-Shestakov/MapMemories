package com.example.mapmemories;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MemoriesAdapter extends RecyclerView.Adapter<MemoriesAdapter.ViewHolder> {

    private Context context;
    private List<Post> postList;
    private OnPostClickListener listener;

    // Добавили Firebase напрямую, чтобы не зависеть от PostUtils и избежать вылетов
    private String currentUserId;
    private DatabaseReference postsRef;

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    public MemoriesAdapter(Context context, List<Post> postList, OnPostClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;

        // Инициализация
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        currentUserId = user != null ? user.getUid() : "";
        postsRef = FirebaseDatabase.getInstance().getReference("posts");

        // Включаем стабильные ID для плавной анимации
        setHasStableIds(true);
    }

    @Override
    public long getItemId(int position) {
        return postList.get(position).getId().hashCode();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_privat_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        // 1. Текстовые данные
        holder.title.setText(post.getTitle());
        holder.description.setText(post.getDescription());

        // Форматирование координат
        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinates.setText(coords);

        // 2. Медиа
        Glide.with(context)
                .load(post.getMediaUrl())
                .placeholder(R.color.secondary) // Цвет фона пока грузится
                .centerCrop()
                .into(holder.postImage);

        // Иконка видео
        if ("video".equals(post.getMediaType())) {
            holder.videoIcon.setVisibility(View.VISIBLE);
        } else {
            holder.videoIcon.setVisibility(View.GONE);
        }

        // 3. ПРИВАТНОСТЬ И ЛАЙКИ
        if (post.isPublic()) {
            // --- ПУБЛИЧНЫЙ ПОСТ ---
            holder.privacyIcon.setVisibility(View.GONE);     // Скрываем замок
            holder.likeContainer.setVisibility(View.VISIBLE);// Показываем лайки

            setupLikeLogic(holder, post); // Настраиваем лайки
        } else {
            // --- ПРИВАТНЫЙ ПОСТ ---
            holder.privacyIcon.setVisibility(View.VISIBLE);  // Показываем замок
            holder.likeContainer.setVisibility(View.GONE);   // СКРЫВАЕМ лайки (это решает баг с подсчетом!)
        }

        // Клик по всей карточке
        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));
    }

    private void setupLikeLogic(ViewHolder holder, Post post) {
        // Подсчет количества
        int count = post.getLikeCount();
        holder.likeCount.setText(String.valueOf(count));

        // Состояние кнопки (лайкнул я или нет)
        boolean isLikedByMe = post.getLikes() != null && post.getLikes().containsKey(currentUserId);

        if (isLikedByMe) {
            holder.likeIcon.setImageResource(R.drawable.ic_favorite_red); // Или ic_favorite
            holder.likeIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent));
        } else {
            holder.likeIcon.setImageResource(R.drawable.ic_favorite_border); // Или ic_favorite_border
            holder.likeIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
        }

        // Клик по лайку
        holder.likeContainer.setOnClickListener(v -> {
            if (currentUserId.isEmpty()) return;

            boolean currentStatus = false;
            if (post.getLikes() != null) {
                currentStatus = post.getLikes().containsKey(currentUserId);
            }
            boolean newStatus = !currentStatus;

            // Анимация
            if (newStatus) {
                AnimUtils.animateLike(holder.likeIcon, true);
                holder.likeIcon.setImageResource(R.drawable.ic_favorite_red);
                holder.likeIcon.setColorFilter(ContextCompat.getColor(context, R.color.accent));
                holder.likeCount.setText(String.valueOf(count + 1));
            } else {
                AnimUtils.animateLike(holder.likeIcon, false);
                holder.likeIcon.setImageResource(R.drawable.ic_favorite_border);
                holder.likeIcon.setColorFilter(ContextCompat.getColor(context, R.color.text_secondary));
                holder.likeCount.setText(String.valueOf(count > 0 ? count - 1 : 0));
            }

            // Отправка в базу
            postsRef.child(post.getId()).child("likes").child(currentUserId)
                    .setValue(newStatus ? true : null);
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView postImage, videoIcon, privacyIcon, likeIcon;
        TextView title, description, coordinates, likeCount;
        LinearLayout likeContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Убедись, что все эти ID есть в item_memory_privat_card.xml
            postImage = itemView.findViewById(R.id.postImage);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            privacyIcon = itemView.findViewById(R.id.privacyIcon);
            title = itemView.findViewById(R.id.postTitle);
            description = itemView.findViewById(R.id.postDescription);
            coordinates = itemView.findViewById(R.id.coordinatesText);

            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCount = itemView.findViewById(R.id.likeCount);
            likeContainer = itemView.findViewById(R.id.likeContainer);
        }
    }
}
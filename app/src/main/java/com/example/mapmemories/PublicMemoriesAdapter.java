package com.example.mapmemories;

import android.content.Context;
import android.content.Intent;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PublicMemoriesAdapter extends RecyclerView.Adapter<PublicMemoriesAdapter.ViewHolder> {

    private Context context;
    private List<Post> postList;
    private OnPostClickListener listener;

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    public PublicMemoriesAdapter(Context context, List<Post> postList, OnPostClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Используем обновленный layout item_post_public
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_public_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.title.setText(post.getTitle());

        String dateString = DateFormat.format("dd MMM yyyy, HH:mm", new Date(post.getTimestamp())).toString();
        holder.date.setText(dateString);

        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinates.setText(coords);

        // Загрузка медиа
        Glide.with(context)
                .load(post.getMediaUrl())
                .placeholder(R.color.secondary)
                .centerCrop()
                .into(holder.postImage);

        if ("video".equals(post.getMediaType())) {
            holder.videoIcon.setVisibility(View.VISIBLE);
        } else {
            holder.videoIcon.setVisibility(View.GONE);
        }

        // Загрузка автора
        loadAuthorInfo(post.getUserId(), holder);

        // === ЛОГИКА ЛАЙКОВ ===
        // 1. Привязываем UI к данным (отображаем красное/серое сердце и число)
        PostUtils.bindLikeButton(post.getId(), holder.likeIcon, holder.likeCount);

        // 2. Обработка клика по лайку
        holder.likeContainer.setOnClickListener(v -> {
            // Анимация
            holder.likeIcon.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> {
                holder.likeIcon.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            });
            // Переключаем в базе
            PostUtils.toggleLike(post);
        });
        // =====================

        // Клик по карточке
        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));

        // Клик по автору (переход в профиль)
        View.OnClickListener authorClick = v -> {
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("targetUserId", post.getUserId());
            context.startActivity(intent);
        };
        holder.authorAvatar.setOnClickListener(authorClick);
        holder.authorName.setOnClickListener(authorClick);
    }

    private void loadAuthorInfo(String userId, ViewHolder holder) {
        FirebaseDatabase.getInstance().getReference("users").child(userId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String name = snapshot.child("username").getValue(String.class);
                        String avatar = snapshot.child("profileImageUrl").getValue(String.class);

                        holder.authorName.setText(name != null ? name : "Пользователь");

                        if (avatar != null && !avatar.isEmpty()) {
                            Glide.with(context).load(avatar).circleCrop().into(holder.authorAvatar);
                        } else {
                            holder.authorAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                        }
                    }
                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView postImage, videoIcon, authorAvatar, likeIcon;
        TextView title, date, coordinates, authorName, likeCount;
        LinearLayout likeContainer;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            postImage = itemView.findViewById(R.id.postImage);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            authorAvatar = itemView.findViewById(R.id.authorAvatarInPublic);
            authorName = itemView.findViewById(R.id.authorNameInPublic);
            title = itemView.findViewById(R.id.postTitle);
            date = itemView.findViewById(R.id.postDateInPublic);
            coordinates = itemView.findViewById(R.id.coordinatesText);

            // Лайки
            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCount = itemView.findViewById(R.id.likeCount);
            likeContainer = itemView.findViewById(R.id.likeContainer);
        }
    }
}
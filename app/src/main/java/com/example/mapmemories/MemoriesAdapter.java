package com.example.mapmemories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.util.List;
import java.util.Locale;

public class MemoriesAdapter extends RecyclerView.Adapter<MemoriesAdapter.ViewHolder> {

    private Context context;
    private List<Post> postList;
    private OnPostClickListener listener;

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    public MemoriesAdapter(Context context, List<Post> postList, OnPostClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Используем обновленный item_memory
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_privat_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.title.setText(post.getTitle());
        holder.description.setText(post.getDescription());

        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinates.setText(coords);

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

        // Приватность
        if (post.isPublic()) {
            holder.privacyIcon.setVisibility(View.GONE); // Публичный - иконки замка нет
        } else {
            holder.privacyIcon.setVisibility(View.VISIBLE); // Приватный - иконка замка
        }

        // === ЛОГИКА ЛАЙКОВ ===
        // Даже в своем профиле интересно видеть, сколько лайков собрал пост
        PostUtils.bindLikeButton(post.getId(), holder.likeIcon, holder.likeCount);

        // Можно разрешить лайкать свои посты (это стандартная практика)
        holder.likeContainer.setOnClickListener(v -> {
            holder.likeIcon.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> {
                holder.likeIcon.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            });
            PostUtils.toggleLike(post);
        });

        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));
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
            postImage = itemView.findViewById(R.id.postImage);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            privacyIcon = itemView.findViewById(R.id.privacyIcon);
            title = itemView.findViewById(R.id.postTitle);
            description = itemView.findViewById(R.id.postDescription);
            coordinates = itemView.findViewById(R.id.coordinatesText);

            // Лайки
            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCount = itemView.findViewById(R.id.likeCount);
            likeContainer = itemView.findViewById(R.id.likeContainer);
        }
    }
}
package com.example.mapmemories;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

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
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_privat_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.title.setText(post.getTitle());
        holder.description.setText(post.getDescription());

        if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
            Glide.with(context)
                    .load(post.getMediaUrl())
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .centerCrop()
                    .into(holder.image);
        }

        // Иконка видео
        if ("video".equals(post.getMediaType())) {
            holder.videoIcon.setVisibility(View.VISIBLE);
        } else {
            holder.videoIcon.setVisibility(View.GONE);
        }

        if (post.isPublic()) {
            holder.privacyIcon.setImageResource(R.drawable.ic_public);
        } else {
            holder.privacyIcon.setImageResource(R.drawable.ic_lock);
        }

        // 2. Логика Координат
        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinatesText.setText(coords);

        // Клик по координатам (заглушка)
        holder.coordinatesLayout.setOnClickListener(v -> {
            Toast.makeText(context, "Открыть карту: " + coords, Toast.LENGTH_SHORT).show();
        });

        // Клик по всей карточке
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPostClick(post);
            }
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView image, videoIcon, privacyIcon;
        TextView title, description, coordinatesText;
        View coordinatesLayout;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.postImage);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            privacyIcon = itemView.findViewById(R.id.privacyIcon);
            title = itemView.findViewById(R.id.postTitle);
            description = itemView.findViewById(R.id.postDescription);
            coordinatesText = itemView.findViewById(R.id.coordinatesText);
            coordinatesLayout = itemView.findViewById(R.id.coordinatesLayout);
        }
    }
}
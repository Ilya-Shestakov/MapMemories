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
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.request.RequestOptions;

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
        // Убедись, что layout item_memory_privat_card существует и в нем есть нужные ID
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
                    .apply(new RequestOptions().transform(new CenterCrop(), new RoundedCorners(16)))
                    .placeholder(R.drawable.ic_profile_placeholder)
                    .into(holder.image);
        } else {
            holder.image.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Иконка видео
        if ("video".equals(post.getMediaType())) {
            holder.videoIcon.setVisibility(View.VISIBLE);
        } else {
            holder.videoIcon.setVisibility(View.GONE);
        }

        // Логика приватности (Теперь работает корректно благодаря Post.java)
        if (post.isPublic()) {
            holder.privacyIcon.setImageResource(R.drawable.ic_public); // Иконка открытого замка/глобуса
        } else {
            holder.privacyIcon.setImageResource(R.drawable.ic_lock);   // Иконка закрытого замка
        }

        // Координаты
        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinatesText.setText(coords);

        // Клик по координатам
        holder.coordinatesLayout.setOnClickListener(v -> {
            // Здесь можно добавить открытие карты
            Toast.makeText(context, "Координаты: " + coords, Toast.LENGTH_SHORT).show();
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
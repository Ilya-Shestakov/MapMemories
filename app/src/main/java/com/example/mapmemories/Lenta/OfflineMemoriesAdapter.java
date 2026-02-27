package com.example.mapmemories.Lenta;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mapmemories.R;
import com.example.mapmemories.database.OfflinePost;

import java.util.List;

public class OfflineMemoriesAdapter extends RecyclerView.Adapter<OfflineMemoriesAdapter.ViewHolder> {

    private Context context;
    private List<OfflinePost> offlinePosts;
    private OnDeleteClickListener deleteListener;

    // Интерфейс для клика по кнопке удаления
    public interface OnDeleteClickListener {
        void onDeleteClick(OfflinePost post);
    }

    public OfflineMemoriesAdapter(Context context, List<OfflinePost> offlinePosts, OnDeleteClickListener deleteListener) {
        this.context = context;
        this.offlinePosts = offlinePosts;
        this.deleteListener = deleteListener;
    }

    public void updateList(List<OfflinePost> newPosts) {
        this.offlinePosts = newPosts;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_offline_post, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OfflinePost post = offlinePosts.get(position);

        holder.tvTitle.setText(post.title);

        // Загружаем картинку прямо с файла на телефоне
        Glide.with(context)
                .load(post.mediaUriString)
                .centerCrop()
                .placeholder(R.drawable.circle_background) // твоя заглушка
                .into(holder.ivThumbnail);

        holder.btnDelete.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onDeleteClick(post);
            }
        });
    }

    @Override
    public int getItemCount() {
        return offlinePosts.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivThumbnail, btnDelete;
        TextView tvTitle, tvStatus;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivThumbnail = itemView.findViewById(R.id.ivThumbnail);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            tvTitle = itemView.findViewById(R.id.tvTitle);
            tvStatus = itemView.findViewById(R.id.tvStatus);
        }
    }
}
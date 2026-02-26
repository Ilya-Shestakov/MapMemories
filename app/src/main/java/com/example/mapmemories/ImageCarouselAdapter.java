package com.example.mapmemories;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import java.util.List;

public class ImageCarouselAdapter extends RecyclerView.Adapter<ImageCarouselAdapter.ViewHolder> {

    private final Context context;
    private List<String> imageUrls;
    private List<Uri> imageUris;
    private OnImageClickListener listener;

    public interface OnImageClickListener {
        void onImageClick(int position, String url);
    }

    // Для ссылок из интернета (Лента, Просмотр)
    public ImageCarouselAdapter(Context context, List<String> imageUrls, OnImageClickListener listener) {
        this.context = context;
        this.imageUrls = imageUrls;
        this.listener = listener;
    }

    // Для локальных файлов (Создание поста)
    public ImageCarouselAdapter(Context context, List<Uri> imageUris) {
        this.context = context;
        this.imageUris = imageUris;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_carousel_image, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Object imageToLoad = (imageUrls != null) ? imageUrls.get(position) : imageUris.get(position);

        Glide.with(context)
                .load(imageToLoad)
                .transition(DrawableTransitionOptions.withCrossFade(200))
                .into(holder.imageView);

        holder.imageView.setOnClickListener(v -> {
            if (listener != null && imageUrls != null) {
                listener.onImageClick(position, imageUrls.get(position));
            }
        });
    }

    @Override
    public int getItemCount() {
        return (imageUrls != null) ? imageUrls.size() : (imageUris != null ? imageUris.size() : 0);
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.carouselImageView);
        }
    }
}
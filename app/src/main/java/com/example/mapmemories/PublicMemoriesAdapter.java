package com.example.mapmemories;

import android.content.Context;
import android.text.TextUtils;
import android.text.format.DateFormat;
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
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
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
        // Используем новый layout
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_public_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.postTitle.setText(post.getTitle());

        // Форматируем дату
        String dateString = DateFormat.format("dd MMM yyyy", new Date(post.getTimestamp())).toString();
        holder.postDate.setText(dateString);

        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinatesText.setText(coords);

        // Загрузка фото поста
        if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
            Glide.with(context)
                    .load(post.getMediaUrl())
                    .placeholder(R.drawable.ic_profile_placeholder) // можно заменить на другую заглушку для постов
                    .into(holder.postImage);
        } else {
            holder.postImage.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Видео иконка
        holder.videoIcon.setVisibility("video".equals(post.getMediaType()) ? View.VISIBLE : View.GONE);

        // 2. ЗАГРУЖАЕМ ДАННЫЕ АВТОРА (Имя и Аватар)
        loadAuthorInfo(post.getUserId(), holder);

        // 3. Клики
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onPostClick(post);
        });

        // Клик по координатам (опционально)
        holder.coordinatesLayout.setOnClickListener(v -> {
            Toast.makeText(context, "Координаты скопированы", Toast.LENGTH_SHORT).show();
        });
    }

    // Метод для подгрузки данных юзера из Firebase
    private void loadAuthorInfo(String userId, ViewHolder holder) {
        if (userId == null) return;

        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);

        // Используем addListenerForSingleValueEvent, чтобы загрузить один раз и не держать соединение открытым лишний раз
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String avatarUrl = snapshot.child("profileImageUrl").getValue(String.class);

                    holder.authorName.setText(TextUtils.isEmpty(username) ? "Неизвестный" : username);

                    if (!TextUtils.isEmpty(avatarUrl)) {
                        Glide.with(context)
                                .load(avatarUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(holder.authorAvatar);
                    } else {
                        holder.authorAvatar.setImageResource(R.drawable.ic_profile_placeholder);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Ошибка загрузки автора
            }
        });
    }

    @Override
    public int getItemCount() {
        return postList.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        // --- ВОТ ЭТИ СТРОКИ ТЫ СКОРЕЕ ВСЕГО ПРОПУСТИЛ ---
        ImageView authorAvatar, postImage, videoIcon;
        TextView authorName, postDate, postTitle, coordinatesText;
        View coordinatesLayout;
        // -----------------------------------------------

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            // Здесь ошибки исчезнут, так как переменные объявлены выше
            authorAvatar = itemView.findViewById(R.id.authorAvatarInPublic);
            authorName = itemView.findViewById(R.id.authorNameInPublic);
            postDate = itemView.findViewById(R.id.postDateInPublic);

            postImage = itemView.findViewById(R.id.postImage);
            videoIcon = itemView.findViewById(R.id.videoIcon);
            postTitle = itemView.findViewById(R.id.postTitle);
            coordinatesText = itemView.findViewById(R.id.coordinatesText);
            coordinatesLayout = itemView.findViewById(R.id.coordinatesLayout);
        }
    }
}
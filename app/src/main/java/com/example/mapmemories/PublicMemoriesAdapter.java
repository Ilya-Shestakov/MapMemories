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
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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

    // Firebase references
    private String currentUserId;
    private DatabaseReference usersRef;
    private DatabaseReference postsRef;

    // !!! НОВОЕ ПОЛЕ: Флаг, можно ли кликать по автору !!!
    private boolean isAuthorClickable = true;

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    public PublicMemoriesAdapter(Context context, List<Post> postList, OnPostClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;

        setHasStableIds(true);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            this.currentUserId = user.getUid();
        } else {
            this.currentUserId = "";
        }

        this.usersRef = FirebaseDatabase.getInstance().getReference("users");
        this.postsRef = FirebaseDatabase.getInstance().getReference("posts");
    }

    // !!! НОВЫЙ МЕТОД: Сеттер для отключения кликов !!!
    public void setAuthorClickable(boolean clickable) {
        this.isAuthorClickable = clickable;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_public_card, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public long getItemId(int position) {
        return postList.get(position).getId().hashCode();
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        // ... (Твой код заполнения текстов и картинок без изменений) ...
        holder.title.setText(post.getTitle());
        String dateString = DateFormat.format("dd MMM yyyy, HH:mm", new Date(post.getTimestamp())).toString();
        holder.date.setText(dateString);
        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        holder.coordinates.setText(coords);

        Glide.with(context)
                .load(post.getMediaUrl())
                .placeholder(R.color.secondary)
                .centerCrop()
                .into(holder.postImage);

        holder.videoIcon.setVisibility("video".equals(post.getMediaType()) ? View.VISIBLE : View.GONE);

        loadAuthorInfo(post.getUserId(), holder);

        // ... (Логика лайков без изменений) ...
        int count = post.getLikeCount();
        holder.likeCount.setText(String.valueOf(count));

        boolean isLikedByMe = false;
        if (post.getLikes() != null && currentUserId != null) {
            isLikedByMe = post.getLikes().containsKey(currentUserId);
        }
        updateLikeIconState(holder.likeIcon, isLikedByMe);

        holder.likeContainer.setOnClickListener(v -> {
            if (currentUserId.isEmpty()) return;
            boolean currentStatus = false;
            if (post.getLikes() != null) {
                currentStatus = post.getLikes().containsKey(currentUserId);
            }
            boolean newStatus = !currentStatus;
            updateLikeIconState(holder.likeIcon, newStatus);
            int currentCount = post.getLikeCount();
            if (newStatus) currentCount++; else currentCount--;
            holder.likeCount.setText(String.valueOf(currentCount));
            AnimUtils.animateLike(holder.likeIcon, newStatus);
            postsRef.child(post.getId()).child("likes").child(currentUserId)
                    .setValue(newStatus ? true : null);
        });

        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));

        // !!! ИЗМЕНЕНИЕ ЗДЕСЬ: Проверяем флаг перед установкой слушателя !!!
        if (isAuthorClickable) {
            View.OnClickListener authorClick = v -> {
                // Проверка, чтобы не открывать свой же профиль, если мы в ленте (опционально)
                // if (post.getUserId().equals(currentUserId)) return;

                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("targetUserId", post.getUserId());
                context.startActivity(intent);
            };
            holder.authorAvatar.setOnClickListener(authorClick);
            holder.authorName.setOnClickListener(authorClick);
        } else {
            // Если клики запрещены, убираем слушатели (на случай переиспользования View)
            holder.authorAvatar.setOnClickListener(null);
            holder.authorName.setOnClickListener(null);
            // Можно также убрать эффект нажатия (ripple), если нужно:
            holder.authorAvatar.setClickable(false);
            holder.authorName.setClickable(false);
        }
    }

    // ... (Остальные методы без изменений) ...
    private void updateLikeIconState(ImageView icon, boolean isLiked) {
        if (isLiked) {
            icon.setImageResource(R.drawable.ic_favorite_red);
            icon.setColorFilter(ContextCompat.getColor(context, R.color.accent));
        } else {
            icon.setImageResource(R.drawable.ic_favorite_border);
            icon.setColorFilter(ContextCompat.getColor(context, R.color.text_primary));
        }
    }

    private void loadAuthorInfo(String userId, ViewHolder holder) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (!snapshot.exists()) return;
                String name = snapshot.child("username").getValue(String.class);
                String avatar = snapshot.child("profileImageUrl").getValue(String.class);
                holder.authorName.setText(name != null ? name : "Пользователь");
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(context).load(avatar).placeholder(R.drawable.ic_profile_placeholder).circleCrop().into(holder.authorAvatar);
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
            likeIcon = itemView.findViewById(R.id.likeIcon);
            likeCount = itemView.findViewById(R.id.likeCount);
            likeContainer = itemView.findViewById(R.id.likeContainer);
        }
    }
}
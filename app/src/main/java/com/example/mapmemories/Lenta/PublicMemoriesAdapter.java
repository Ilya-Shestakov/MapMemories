package com.example.mapmemories.Lenta;

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
import androidx.viewpager2.widget.ViewPager2;
import com.bumptech.glide.Glide;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.AnimUtils;
import com.example.mapmemories.systemHelpers.ImageCarouselAdapter;
import com.example.mapmemories.systemHelpers.Post;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class PublicMemoriesAdapter extends RecyclerView.Adapter<PublicMemoriesAdapter.ViewHolder> {

    private final Context context;
    private final List<Post> postList;
    private final OnPostClickListener listener;
    private final String currentUserId;
    private final DatabaseReference usersRef;
    private final DatabaseReference postsRef;

    private boolean isAuthorClickable = true;

    public interface OnPostClickListener {
        void onPostClick(Post post);
    }

    public PublicMemoriesAdapter(Context context, List<Post> postList, OnPostClickListener listener) {
        this.context = context;
        this.postList = postList;
        this.listener = listener;
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        this.currentUserId = (user != null) ? user.getUid() : "";
        this.usersRef = FirebaseDatabase.getInstance().getReference("users");
        this.postsRef = FirebaseDatabase.getInstance().getReference("posts");
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_memory_public_card, parent, false);
        return new ViewHolder(view);
    }

    public void setAuthorClickable(boolean clickable) {
        this.isAuthorClickable = clickable;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Post post = postList.get(position);

        holder.title.setText(post.getTitle());
        holder.date.setText(DateFormat.format("dd MMM yyyy, HH:mm", new Date(post.getTimestamp())).toString());
        holder.coordinates.setText(String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude()));

        // Настройка карусели
        List<String> urls = post.getMediaUrls();
        if (urls == null || urls.isEmpty()) {
            urls = new ArrayList<>();
            if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
                urls.add(post.getMediaUrl()); // Совместимость со старыми постами
            }
        }

        ImageCarouselAdapter carouselAdapter = new ImageCarouselAdapter(context, urls, (pos, url) -> listener.onPostClick(post));
        holder.viewPagerMedia.setAdapter(carouselAdapter);

        if (urls.size() > 1) {
            holder.tabLayoutDots.setVisibility(View.VISIBLE);
            new TabLayoutMediator(holder.tabLayoutDots, holder.viewPagerMedia, (tab, pos) -> {}).attach();
        } else {
            holder.tabLayoutDots.setVisibility(View.GONE);
        }

        holder.videoIcon.setVisibility("video".equals(post.getMediaType()) ? View.VISIBLE : View.GONE);
        loadAuthorInfo(post.getUserId(), holder);

        // Лайки
        holder.likeCount.setText(String.valueOf(post.getLikeCount()));
        boolean isLikedByMe = post.getLikes() != null && !currentUserId.isEmpty() && post.getLikes().containsKey(currentUserId);
        updateLikeIconState(holder.likeIcon, isLikedByMe);

        holder.likeContainer.setOnClickListener(v -> {
            if (currentUserId.isEmpty()) return;

            boolean isCurrentlyLiked = post.getLikes() != null && post.getLikes().containsKey(currentUserId);
            boolean newStatus = !isCurrentlyLiked;

            updateLikeIconState(holder.likeIcon, newStatus);
            holder.likeCount.setText(String.valueOf(newStatus ? post.getLikeCount() + 1 : post.getLikeCount() - 1));
            AnimUtils.animateLike(holder.likeIcon, newStatus);

            postsRef.child(post.getId()).child("likes").child(currentUserId).setValue(newStatus ? true : null);

            DatabaseReference authorRef = usersRef.child(post.getUserId()).child("likesCount");
            if (newStatus) {
                authorRef.setValue(com.google.firebase.database.ServerValue.increment(1));
            } else {
                authorRef.setValue(com.google.firebase.database.ServerValue.increment(-1));
            }
        });

        // Клик по карточке (кроме карусели, она обрабатывается внутри адаптера)
        holder.itemView.setOnClickListener(v -> listener.onPostClick(post));

        if (isAuthorClickable) {
            View.OnClickListener authorClick = v -> {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("targetUserId", post.getUserId());
                context.startActivity(intent);
            };
            holder.authorAvatar.setOnClickListener(authorClick);
            holder.authorName.setOnClickListener(authorClick);
        } else {
            holder.authorAvatar.setOnClickListener(null);
            holder.authorName.setOnClickListener(null);
            holder.authorAvatar.setClickable(false);
            holder.authorName.setClickable(false);
        }
    }

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
                holder.authorName.setText(snapshot.child("username").getValue(String.class));
                String avatar = snapshot.child("profileImageUrl").getValue(String.class);
                if (avatar != null && !avatar.isEmpty()) {
                    Glide.with(context).load(avatar).placeholder(R.drawable.ic_profile_placeholder).circleCrop().into(holder.authorAvatar);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public int getItemCount() { return postList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView videoIcon, authorAvatar, likeIcon;
        TextView title, date, coordinates, authorName, likeCount;
        LinearLayout likeContainer;
        ViewPager2 viewPagerMedia;
        TabLayout tabLayoutDots;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            viewPagerMedia = itemView.findViewById(R.id.viewPagerMedia);
            tabLayoutDots = itemView.findViewById(R.id.tabLayoutDots);
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
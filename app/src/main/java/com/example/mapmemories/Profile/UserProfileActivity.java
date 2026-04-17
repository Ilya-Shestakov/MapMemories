package com.example.mapmemories.Profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Chats.ChatActivity;
import com.example.mapmemories.Post.ViewPostDetailsActivity;
import com.example.mapmemories.Lenta.PublicMemoriesAdapter;
import com.example.mapmemories.R;
import com.example.mapmemories.Post.Post;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

public class UserProfileActivity extends AppCompatActivity {

    private String targetUserId;
    private String currentUserId;

    private View onlineIndicator;
    private TextView profileUsername, profileStatus, profileAbout, profileJoinDate;
    private TextView countMemories, countLikes, followersCount, followingCount;
    private RecyclerView userPostsRecyclerView;
    private ImageButton btnFriendAction, btnChat;

    // Zoom & Аватарки
    private ViewPager2 profileImageViewPager, expandedViewPager;
    private AvatarAdapter avatarAdapter, expandedAdapter;
    private List<String> avatarUrls = new ArrayList<>();

    private View mainContentLayout, expandedBackground;
    private FrameLayout expandedContainer;
    private MaterialCardView expandedCard;
    private LinearLayout expandedActionsContainer;
    private ImageButton btnShareExpanded, btnDownloadExpanded;
    private Animator currentAnimator;

    private DatabaseReference userRef, postsRef, rootRef;
    private PublicMemoriesAdapter adapter;
    private List<Post> userPostList;

    private static final String STATE_NOT_FOLLOWING = "not_following";
    private static final String STATE_FOLLOWING = "following";
    private String currentFollowState = STATE_NOT_FOLLOWING;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_profile);

        targetUserId = getIntent().getStringExtra("targetUserId");
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();

        if (TextUtils.isEmpty(targetUserId) || currentUser == null) {
            finish();
            return;
        }
        currentUserId = currentUser.getUid();

        initViews();
        setupFirebase();
        setupAvatarPagers();

        if (currentUserId.equals(targetUserId)) {
            btnFriendAction.setVisibility(View.GONE);
            btnChat.setVisibility(View.GONE);
            profileStatus.setVisibility(View.GONE);
            onlineIndicator.setVisibility(View.GONE);
        } else {
            btnFriendAction.setOnClickListener(v -> performFollowAction());
            btnChat.setOnClickListener(v -> {
                Intent intent = new Intent(UserProfileActivity.this, ChatActivity.class);
                intent.putExtra("targetUserId", targetUserId);
                startActivity(intent);
            });
            checkFollowStatus();
        }

        loadUserInfo();
        setupRecyclerView();
        loadUserPublicPosts();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }

        toolbar.setNavigationOnClickListener(v -> finish());

        mainContentLayout = findViewById(R.id.mainContentLayout);
        profileImageViewPager = findViewById(R.id.profileImageViewPager);
        onlineIndicator = findViewById(R.id.onlineIndicator);
        profileUsername = findViewById(R.id.profileUsername);
        profileStatus = findViewById(R.id.profileStatus);
        profileAbout = findViewById(R.id.profileAbout);
        profileJoinDate = findViewById(R.id.profileJoinDate);

        countMemories = findViewById(R.id.countMemories);
        countLikes = findViewById(R.id.countLikes);
        followersCount = findViewById(R.id.followersCount);
        followingCount = findViewById(R.id.followingCount);

        userPostsRecyclerView = findViewById(R.id.userPostsRecyclerView);
        btnFriendAction = findViewById(R.id.btnFriendAction);
        btnChat = findViewById(R.id.btnChat);

        // Расширенный просмотр
        expandedContainer = findViewById(R.id.expandedContainer);
        expandedBackground = findViewById(R.id.expandedBackground);
        expandedCard = findViewById(R.id.expandedCard);
        expandedViewPager = findViewById(R.id.expandedViewPager);
        expandedActionsContainer = findViewById(R.id.expandedActionsContainer);
        btnShareExpanded = findViewById(R.id.btnShareExpanded);
        btnDownloadExpanded = findViewById(R.id.btnDownloadExpanded);

        btnShareExpanded.setOnClickListener(v -> shareCurrentPhoto());
        btnDownloadExpanded.setOnClickListener(v -> downloadCurrentPhoto());
    }

    private void setupAvatarPagers() {
        avatarAdapter = new AvatarAdapter(avatarUrls, position -> {
            if (!avatarUrls.isEmpty()) zoomImageFromThumb(profileImageViewPager, position);
        });
        profileImageViewPager.setAdapter(avatarAdapter);

        expandedAdapter = new AvatarAdapter(avatarUrls, position -> closeExpandedImage());
        expandedViewPager.setAdapter(expandedAdapter);
    }

    private void setupFirebase() {
        rootRef = FirebaseDatabase.getInstance().getReference();
        userRef = rootRef.child("users").child(targetUserId);
        postsRef = rootRef.child("posts");
    }

    private void loadUserInfo() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;

                if (snapshot.exists()) {
                    String username = snapshot.child("username").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    if (!currentUserId.equals(targetUserId)) {
                        Object statusObj = snapshot.child("status").getValue();
                        boolean isHidden = false;
                        if (snapshot.child("privacy").child("hide_online").exists()) {
                            isHidden = snapshot.child("privacy").child("hide_online").getValue(Boolean.class);
                        }

                        String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                        profileStatus.setText(statusText);

                        if (statusText.equals("в сети")) {
                            onlineIndicator.setVisibility(View.VISIBLE);
                            profileStatus.setTextColor(getResources().getColor(R.color.online_indicator));
                        } else {
                            onlineIndicator.setVisibility(View.GONE);
                            profileStatus.setTextColor(getResources().getColor(R.color.text_secondary));
                        }
                    }

                    long followers = snapshot.child("requests_incoming").getChildrenCount();
                    long following = snapshot.child("requests_sent").getChildrenCount();
                    long memCount = snapshot.child("memoriesCount").exists() ? snapshot.child("memoriesCount").getValue(Long.class) : 0;
                    long likesCnt = snapshot.child("likesCount").exists() ? snapshot.child("likesCount").getValue(Long.class) : 0;

                    profileUsername.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    profileAbout.setText(TextUtils.isEmpty(about) ? "" : about);
                    profileAbout.setVisibility(TextUtils.isEmpty(about) ? View.GONE : View.VISIBLE);

                    followersCount.setText(String.valueOf(followers));
                    followingCount.setText(String.valueOf(following));
                    countMemories.setText(String.valueOf(memCount));
                    countLikes.setText(String.valueOf(likesCnt));

                    if (joinDate != null) {
                        profileJoinDate.setText("На сайте с: " + DateFormat.format("dd.MM.yyyy", new Date(joinDate)));
                    }

                    avatarUrls.clear();
                    if (snapshot.hasChild("profileImages")) {
                        for (DataSnapshot imgSnap : snapshot.child("profileImages").getChildren()) {
                            avatarUrls.add(imgSnap.getValue(String.class));
                        }
                    } else if (snapshot.hasChild("profileImageUrl")) {
                        String oldUrl = snapshot.child("profileImageUrl").getValue(String.class);
                        if (oldUrl != null && !oldUrl.isEmpty()) avatarUrls.add(oldUrl);
                    }
                    avatarAdapter.notifyDataSetChanged();
                    expandedAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // --- ЛОГИКА ПОДПИСКИ С ИКОНКАМИ ---
    private void checkFollowStatus() {
        rootRef.child("users").child(currentUserId).child("requests_sent")
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isFinishing() || isDestroyed()) return;

                        if (snapshot.hasChild(targetUserId)) {
                            currentFollowState = STATE_FOLLOWING;
                            // Иконка "галочка", цвет серый
                            btnFriendAction.setImageResource(R.drawable.ic_check); // Создай если нет
                            btnFriendAction.setBackgroundTintList(getResources().getColorStateList(R.color.secondary));
                            btnFriendAction.setColorFilter(getResources().getColor(R.color.text_primary));
                        } else {
                            currentFollowState = STATE_NOT_FOLLOWING;
                            // Иконка "плюс", цвет акцентный
                            btnFriendAction.setImageResource(R.drawable.ic_add);
                            btnFriendAction.setBackgroundTintList(getResources().getColorStateList(R.color.accent));
                            btnFriendAction.setColorFilter(getResources().getColor(android.R.color.white));
                        }
                        btnFriendAction.setEnabled(true);
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void performFollowAction() {
        btnFriendAction.setEnabled(false);
        if (currentFollowState.equals(STATE_NOT_FOLLOWING)) followUser();
        else unfollowUser();
    }

    private void followUser() {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/requests_sent/" + targetUserId, true);
        updates.put("users/" + targetUserId + "/requests_incoming/" + currentUserId, true);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing() && !task.isSuccessful()) {
                btnFriendAction.setEnabled(true);
            }
        });
    }

    private void unfollowUser() {
        HashMap<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/requests_sent/" + targetUserId, null);
        updates.put("users/" + targetUserId + "/requests_incoming/" + currentUserId, null);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing() && !task.isSuccessful()) {
                btnFriendAction.setEnabled(true);
            }
        });
    }

    private void setupRecyclerView() {
        userPostsRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        userPostList = new ArrayList<>();
        adapter = new PublicMemoriesAdapter(this, userPostList, post -> {
            Intent intent = new Intent(UserProfileActivity.this, ViewPostDetailsActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });
        adapter.setAuthorClickable(false);
        userPostsRecyclerView.setAdapter(adapter);
    }

    private void loadUserPublicPosts() {
        postsRef.orderByChild("userId").equalTo(targetUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing()) return;
                userPostList.clear();
                if (snapshot.exists()) {
                    for (DataSnapshot postSnap : snapshot.getChildren()) {
                        Post post = postSnap.getValue(Post.class);
                        if (post != null && post.isPublic()) userPostList.add(post);
                    }
                    Collections.reverse(userPostList);
                }
                adapter.notifyDataSetChanged();
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    // --- ФУНКЦИИ СКАЧИВАНИЯ И ШАРИНГА ---
    private void downloadCurrentPhoto() {
        if (avatarUrls.isEmpty()) return;
        String url = avatarUrls.get(expandedViewPager.getCurrentItem());

        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        request.setTitle("Сохранение фото");
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "Avatar_" + System.currentTimeMillis() + ".jpg");

        DownloadManager manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
        if (manager != null) {
            manager.enqueue(request);
            Toast.makeText(this, "Загрузка началась...", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareCurrentPhoto() {
        if (avatarUrls.isEmpty()) return;
        String url = avatarUrls.get(expandedViewPager.getCurrentItem());

        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        shareIntent.putExtra(Intent.EXTRA_TEXT, url);
        startActivity(Intent.createChooser(shareIntent, "Поделиться фото"));
    }

    // --- АНИМАЦИИ ОТКРЫТИЯ/ЗАКРЫТИЯ (С блюром) ---
    private void zoomImageFromThumb(final View thumbView, int startPosition) {
        if (currentAnimator != null) currentAnimator.cancel();

        expandedViewPager.setCurrentItem(startPosition, false);
        expandedContainer.setAlpha(0f);
        expandedContainer.setVisibility(View.VISIBLE);

        expandedCard.post(() -> {
            if (isFinishing() || isDestroyed()) return;

            Rect startBounds = new Rect();
            Rect finalBounds = new Rect();
            Point globalOffset = new Point();

            thumbView.getGlobalVisibleRect(startBounds);
            expandedCard.getGlobalVisibleRect(finalBounds, globalOffset);

            startBounds.offset(-globalOffset.x, -globalOffset.y);
            finalBounds.offset(-globalOffset.x, -globalOffset.y);

            float startScale = (float) startBounds.width() / finalBounds.width();
            float startX = startBounds.left - finalBounds.left;
            float startY = startBounds.top - finalBounds.top;

            expandedCard.setPivotX(0f);
            expandedCard.setPivotY(0f);
            expandedCard.setTranslationX(startX);
            expandedCard.setTranslationY(startY);
            expandedCard.setScaleX(startScale);
            expandedCard.setScaleY(startScale);

            expandedActionsContainer.setAlpha(0f);
            expandedBackground.setAlpha(0f);
            expandedContainer.setAlpha(1f);
            thumbView.setAlpha(0f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
            }

            AnimatorSet set = new AnimatorSet();
            set.play(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_X, 0f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_Y, 0f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_X, 1f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_Y, 1f))
                    .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f, 1f))
                    .with(ObjectAnimator.ofFloat(expandedActionsContainer, View.ALPHA, 0f, 1f));

            set.setDuration(300);
            set.setInterpolator(new DecelerateInterpolator());
            set.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { currentAnimator = null; }
            });
            set.start();
            currentAnimator = set;

            expandedBackground.setOnClickListener(v -> closeExpandedImage());
        });
    }

    private void closeExpandedImage() {
        if (currentAnimator != null) currentAnimator.cancel();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mainContentLayout.setRenderEffect(null);

        profileImageViewPager.setCurrentItem(expandedViewPager.getCurrentItem(), false);

        Rect startBounds = new Rect();
        Rect finalBounds = new Rect();
        Point globalOffset = new Point();

        profileImageViewPager.getGlobalVisibleRect(startBounds);
        expandedCard.getGlobalVisibleRect(finalBounds, globalOffset);

        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        float startScale = (float) startBounds.width() / finalBounds.width();
        float startX = startBounds.left - finalBounds.left;
        float startY = startBounds.top - finalBounds.top;

        AnimatorSet closeSet = new AnimatorSet();
        closeSet.play(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_X, startX))
                .with(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_Y, startY))
                .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_X, startScale))
                .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_Y, startScale))
                .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f))
                .with(ObjectAnimator.ofFloat(expandedActionsContainer, View.ALPHA, 0f));

        closeSet.setDuration(250);
        closeSet.setInterpolator(new AccelerateInterpolator());
        closeSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                profileImageViewPager.setAlpha(1f);
                expandedContainer.setVisibility(View.GONE);
                currentAnimator = null;
                expandedCard.setTranslationX(0f);
                expandedCard.setTranslationY(0f);
                expandedCard.setScaleX(1f);
                expandedCard.setScaleY(1f);
            }
        });
        closeSet.start();
        currentAnimator = closeSet;
    }

    @Override
    public void onBackPressed() {
        if (expandedContainer.getVisibility() == View.VISIBLE) closeExpandedImage();
        else super.onBackPressed();
    }

    // --- АДАПТЕР ДЛЯ VIEWPAGER2 ---
    private static class AvatarAdapter extends RecyclerView.Adapter<AvatarAdapter.ViewHolder> {
        private final List<String> urls;
        private final OnItemClickListener listener;

        interface OnItemClickListener { void onItemClick(int position); }

        public AvatarAdapter(List<String> urls, OnItemClickListener listener) {
            this.urls = urls;
            this.listener = listener;
        }

        @NonNull @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_avatar, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (urls.isEmpty()) {
                Glide.with(holder.imageView.getContext()).load(R.drawable.ic_profile_placeholder).into(holder.imageView);
                holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onItemClick(0); });
                return;
            }
            Glide.with(holder.imageView.getContext()).load(urls.get(position)).placeholder(R.drawable.ic_profile_placeholder).into(holder.imageView);
            holder.itemView.setOnClickListener(v -> { if (listener != null) listener.onItemClick(position); });
        }

        @Override public int getItemCount() { return urls.isEmpty() ? 1 : urls.size(); }

        static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView imageView;
            ViewHolder(View itemView) {
                super(itemView);
                imageView = itemView.findViewById(R.id.avatarImageItem);
            }
        }
    }
}
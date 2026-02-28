package com.example.mapmemories.Post;

import android.content.Intent;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mapmemories.systemHelpers.ZoomOutPageTransformer;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Lenta.PickLocationActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.SwipeBackHelper;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.example.mapmemories.systemHelpers.DownloadHelper;
import com.example.mapmemories.systemHelpers.ImageCarouselAdapter;
import com.example.mapmemories.systemHelpers.Post;
import com.github.chrisbanes.photoview.PhotoView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ViewPostDetailsActivity extends AppCompatActivity {

    private String postId;
    private Post currentPost;
    private SwipeBackHelper swipeBackHelper;

    private ImageView detailAuthorAvatar;
    private TextView detailAuthorName, detailDate, detailTitle, detailCoordinates, detailDescription;
    private LinearLayout layoutLocation;
    private ImageView detailLikeIcon;
    private TextView detailLikeCount;
    private LinearLayout detailLikeContainer;
    private View mainContentLayout;

    private TextView photoCounter;

    private ViewPager2 viewPagerMedia;
    private TabLayout tabLayoutDots;

    private FrameLayout expandedContainer;
    private PhotoView expandedImage;
    private MaterialButton btnDownloadExpanded;
    private String currentZoomedUrl = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_view_post_details);

        postId = getIntent().getStringExtra("postId");
        if (postId == null) {
            Toast.makeText(this, "Ошибка: пост не найден", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        initViews();
        loadPostData();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        detailAuthorAvatar = findViewById(R.id.detailAuthorAvatar);
        detailAuthorName = findViewById(R.id.detailAuthorName);
        detailDate = findViewById(R.id.detailDate);
        detailTitle = findViewById(R.id.detailTitle);
        detailCoordinates = findViewById(R.id.detailCoordinates);
        detailDescription = findViewById(R.id.detailDescription);
        layoutLocation = findViewById(R.id.layoutLocation);
        mainContentLayout = findViewById(R.id.mainContentLayout);
        detailLikeIcon = findViewById(R.id.detailLikeIcon);
        detailLikeCount = findViewById(R.id.detailLikeCount);
        detailLikeContainer = findViewById(R.id.detailLikeContainer);

        photoCounter = findViewById(R.id.photoCounter);

        viewPagerMedia = findViewById(R.id.viewPagerMedia);
        tabLayoutDots = findViewById(R.id.tabLayoutDots);

        expandedContainer = findViewById(R.id.expandedContainer);
        expandedImage = findViewById(R.id.expandedImage);
        btnDownloadExpanded = findViewById(R.id.btnDownloadExpanded);

        swipeBackHelper = new SwipeBackHelper(this);

        layoutLocation.setOnClickListener(v -> {
            if (currentPost != null) {
                Intent intent = new Intent(ViewPostDetailsActivity.this, PickLocationActivity.class);
                intent.putExtra("lat", currentPost.getLatitude());
                intent.putExtra("lng", currentPost.getLongitude());
                intent.putExtra("viewOnly", true);
                startActivity(intent);
            }
        });

        btnDownloadExpanded.setOnClickListener(v -> {
            if (!currentZoomedUrl.isEmpty()) {
                DownloadHelper.downloadImage(this, currentZoomedUrl);
            }
        });

        expandedImage.setOnClickListener(v -> closeZoom());
    }

    private void loadPostData() {
        DatabaseReference postRef = FirebaseDatabase.getInstance().getReference("posts").child(postId);
        postRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Post post = snapshot.getValue(Post.class);
                if (post != null) {
                    currentPost = post;
                    updateUI(post);
                    loadAuthorInfo(post.getUserId());
                } else {
                    Toast.makeText(ViewPostDetailsActivity.this, "Пост был удален", Toast.LENGTH_SHORT).show();
                    finish();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateUI(Post post) {
        detailTitle.setText(post.getTitle());
        detailDescription.setText(post.getDescription());
        detailDate.setText(DateFormat.format("dd MMMM yyyy, HH:mm", new Date(post.getTimestamp())).toString());
        detailCoordinates.setText(String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude()));

        List<String> urls = post.getMediaUrls();
        if (urls == null || urls.isEmpty()) {
            urls = new ArrayList<>();
            if (post.getMediaUrl() != null && !post.getMediaUrl().isEmpty()) {
                urls.add(post.getMediaUrl());
            }
        }

        if (!urls.isEmpty()) {
            ImageCarouselAdapter adapter = new ImageCarouselAdapter(this, urls, (pos, url) -> openFullScreenZoom(url));
            viewPagerMedia.setAdapter(adapter);

            // ПРИМЕНЯЕМ АНИМАЦИЮ
            viewPagerMedia.setPageTransformer(new ZoomOutPageTransformer());

            if (urls.size() > 1) {
                tabLayoutDots.setVisibility(View.VISIBLE);
                photoCounter.setVisibility(View.VISIBLE);
                new TabLayoutMediator(tabLayoutDots, viewPagerMedia, (tab, pos) -> {}).attach();

                final int totalPhotos = urls.size();
                photoCounter.setText("1/" + totalPhotos);

                viewPagerMedia.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
                    @Override
                    public void onPageSelected(int position) {
                        photoCounter.setText((position + 1) + "/" + totalPhotos);
                    }
                });
            } else {
                tabLayoutDots.setVisibility(View.GONE);
                photoCounter.setVisibility(View.GONE);
            }
        }


        PostUtils.bindLikeButton(post.getId(), detailLikeIcon, detailLikeCount);
        detailLikeContainer.setOnClickListener(v -> {
            detailLikeIcon.animate().scaleX(0.8f).scaleY(0.8f).setDuration(100).withEndAction(() -> {
                detailLikeIcon.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
            });
            PostUtils.toggleLike(post);
        });
    }

    private void loadAuthorInfo(String userId) {
        if (userId == null) return;
        View.OnClickListener openProfile = v -> {
            Intent intent = new Intent(ViewPostDetailsActivity.this, UserProfileActivity.class);
            intent.putExtra("targetUserId", userId);
            startActivity(intent);
        };
        detailAuthorAvatar.setOnClickListener(openProfile);
        detailAuthorName.setOnClickListener(openProfile);
        FirebaseDatabase.getInstance().getReference("users").child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("username").getValue(String.class);
                    String avatar = snapshot.child("profileImageUrl").getValue(String.class);
                    detailAuthorName.setText(TextUtils.isEmpty(name) ? "Неизвестный" : name);
                    if (!TextUtils.isEmpty(avatar)) {
                        Glide.with(ViewPostDetailsActivity.this).load(avatar).placeholder(R.drawable.ic_profile_placeholder).circleCrop().into(detailAuthorAvatar);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void openFullScreenZoom(String imageUrl) {
        if (swipeBackHelper != null) swipeBackHelper.setSwipeEnabled(false);
        currentZoomedUrl = imageUrl;

        expandedContainer.setVisibility(View.VISIBLE);
        expandedContainer.setAlpha(0f);
        expandedContainer.animate().alpha(1f).setDuration(200).start();

        Glide.with(this).load(imageUrl).into(expandedImage);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
        }
    }

    private void closeZoom() {
        if (swipeBackHelper != null) swipeBackHelper.setSwipeEnabled(true);

        expandedContainer.animate().alpha(0f).setDuration(200).withEndAction(() -> {
            expandedContainer.setVisibility(View.GONE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(null);
            }
        }).start();
    }

    @Override
    public void onBackPressed() {
        if (expandedContainer.getVisibility() == View.VISIBLE) {
            closeZoom();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) {
            return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        }
        return super.dispatchTouchEvent(ev);
    }
}
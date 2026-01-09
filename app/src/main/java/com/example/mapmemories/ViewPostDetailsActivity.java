package com.example.mapmemories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.Date;
import java.util.Locale;

public class ViewPostDetailsActivity extends AppCompatActivity {

    private String postId;
    private Post currentPost;

    // UI
    private ImageView detailImage, detailVideoIcon, detailAuthorAvatar;
    private TextView detailAuthorName, detailDate, detailTitle, detailCoordinates, detailDescription;
    private LinearLayout layoutLocation;

    // Лайки
    private ImageView detailLikeIcon;
    private TextView detailLikeCount;
    private LinearLayout detailLikeContainer;

    private View mainContentLayout;

    // Элементы ZOOM
    private FrameLayout expandedContainer;
    private ImageView expandedImage;
    private View expandedBackground;
    private MaterialButton btnDownloadExpanded;
    private Animator currentAnimator;
    private long shortAnimationDuration = 200;

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

        detailImage = findViewById(R.id.detailImage);
        detailVideoIcon = findViewById(R.id.detailVideoIcon);
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

        // Zoom elements
        expandedContainer = findViewById(R.id.expandedContainer);
        expandedImage = findViewById(R.id.expandedImage);
        expandedBackground = findViewById(R.id.expandedBackground);
        btnDownloadExpanded = findViewById(R.id.btnDownloadExpanded);

        layoutLocation.setOnClickListener(v -> {
            if (currentPost != null) {
                Intent intent = new Intent(ViewPostDetailsActivity.this, PickLocationActivity.class);
                intent.putExtra("lat", currentPost.getLatitude());
                intent.putExtra("lng", currentPost.getLongitude());
                intent.putExtra("viewOnly", true);
                startActivity(intent);
            }
        });

        // Открытие зума по клику
        detailImage.setOnClickListener(v -> {
            if (currentPost != null && currentPost.getMediaUrl() != null) {
                zoomImageFromThumb(detailImage, currentPost.getMediaUrl());
            }
        });

        // Скачивание
        btnDownloadExpanded.setOnClickListener(v -> {
            if (currentPost != null && currentPost.getMediaUrl() != null) {
                DownloadHelper.downloadImage(this, currentPost.getMediaUrl());
            }
        });

        // Закрытие зума
        expandedBackground.setOnClickListener(v -> closeZoom());
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
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ViewPostDetailsActivity.this, "Ошибка загрузки", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUI(Post post) {
        detailTitle.setText(post.getTitle());
        detailDescription.setText(post.getDescription());

        String dateString = DateFormat.format("dd MMMM yyyy, HH:mm", new Date(post.getTimestamp())).toString();
        detailDate.setText(dateString);

        String coords = String.format(Locale.US, "%.4f, %.4f", post.getLatitude(), post.getLongitude());
        detailCoordinates.setText(coords);

        if (!TextUtils.isEmpty(post.getMediaUrl())) {
            Glide.with(this).load(post.getMediaUrl()).into(detailImage);
        }

        if ("video".equals(post.getMediaType())) {
            detailVideoIcon.setVisibility(View.VISIBLE);
        } else {
            detailVideoIcon.setVisibility(View.GONE);
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
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    String name = snapshot.child("username").getValue(String.class);
                    String avatar = snapshot.child("profileImageUrl").getValue(String.class);
                    detailAuthorName.setText(TextUtils.isEmpty(name) ? "Неизвестный" : name);
                    if (!TextUtils.isEmpty(avatar)) {
                        Glide.with(ViewPostDetailsActivity.this)
                                .load(avatar)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(detailAuthorAvatar);
                    }
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void zoomImageFromThumb(final View thumbView, String imageUrl) {
        if (currentAnimator != null) currentAnimator.cancel();

        Glide.with(this).load(imageUrl).into(expandedImage);

        final Rect startBounds = new Rect();
        final Rect finalBounds = new Rect();
        final Point globalOffset = new Point();

        thumbView.getGlobalVisibleRect(startBounds);
        findViewById(android.R.id.content).getGlobalVisibleRect(finalBounds, globalOffset);
        startBounds.offset(-globalOffset.x, -globalOffset.y);
        finalBounds.offset(-globalOffset.x, -globalOffset.y);

        float startScale;
        if ((float) finalBounds.width() / finalBounds.height() > (float) startBounds.width() / startBounds.height()) {
            startScale = (float) startBounds.height() / finalBounds.height();
            float startWidth = startScale * finalBounds.width();
            float deltaWidth = (startWidth - startBounds.width()) / 2;
            startBounds.left -= deltaWidth;
            startBounds.right += deltaWidth;
        } else {
            startScale = (float) startBounds.width() / finalBounds.width();
            float startHeight = startScale * finalBounds.height();
            float deltaHeight = (startHeight - startBounds.height()) / 2;
            startBounds.top -= deltaHeight;
            startBounds.bottom += deltaHeight;
        }

        thumbView.setAlpha(0f);
        expandedContainer.setVisibility(View.VISIBLE);

        expandedImage.setPivotX(0f);
        expandedImage.setPivotY(0f);

        AnimatorSet set = new AnimatorSet();
        set.play(ObjectAnimator.ofFloat(expandedImage, View.X, startBounds.left, finalBounds.left))
                .with(ObjectAnimator.ofFloat(expandedImage, View.Y, startBounds.top, finalBounds.top))
                .with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_X, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedImage, View.SCALE_Y, startScale, 1f))
                .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f, 1f))
                .with(ObjectAnimator.ofFloat(btnDownloadExpanded, View.ALPHA, 0f, 1f));

        set.setDuration(shortAnimationDuration);
        set.setInterpolator(new DecelerateInterpolator());
        set.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                currentAnimator = null;
            }
        });
        set.start();
        currentAnimator = set;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
        }
    }

    private void closeZoom() {
        if (currentAnimator != null) currentAnimator.cancel();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mainContentLayout.setRenderEffect(null);
        }

        expandedContainer.setVisibility(View.GONE);
        detailImage.setAlpha(1f);
    }

    @Override
    public void onBackPressed() {
        if (expandedContainer.getVisibility() == View.VISIBLE) {
            closeZoom();
        } else {
            super.onBackPressed();
        }
    }
}


/*


<?xml version="1.0" encoding="utf-8"?>
<FrameLayout xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    xmlns:tools="http://schemas.android.com/tools"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/primary">

    <!-- СЛОЙ 1: ОСНОВНОЙ КОНТЕНТ -->
    <!-- CoordinatorLayout с fitsSystemWindows="true" для корректного отступа -->
    <androidx.coordinatorlayout.widget.CoordinatorLayout
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:fitsSystemWindows="true"
        tools:context=".ViewPostDetailsActivity"
        android:id="@+id/mainContentLayout"
        >

        <com.google.android.material.appbar.AppBarLayout
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:theme="@style/ThemeOverlay.AppCompat.Dark.ActionBar"
            android:background="@android:color/transparent"
            app:elevation="0dp">

            <androidx.appcompat.widget.Toolbar
                android:id="@+id/toolbar"
                android:layout_width="match_parent"
                android:layout_height="?attr/actionBarSize"
                android:background="@color/primary"
                app:navigationIcon="@drawable/ic_arrow_back"
                app:title="Воспоминание"
                app:titleTextColor="@color/text_primary" />
        </com.google.android.material.appbar.AppBarLayout>

        <androidx.core.widget.NestedScrollView
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            app:layout_behavior="@string/appbar_scrolling_view_behavior">

            <LinearLayout
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:orientation="vertical"
                android:paddingBottom="30dp">

                <!-- Картинка поста -->
                <FrameLayout
                    android:layout_width="match_parent"
                    android:layout_height="300dp">

                    <ImageView
                        android:id="@+id/detailImage"
                        android:layout_width="match_parent"
                        android:layout_height="match_parent"
                        android:scaleType="centerCrop"
                        android:src="@color/secondary" />

                    <ImageView
                        android:id="@+id/detailVideoIcon"
                        android:layout_width="64dp"
                        android:layout_height="64dp"
                        android:layout_gravity="center"
                        android:src="@drawable/ic_play_circle"
                        android:visibility="gone"
                        app:tint="@android:color/white" />
                </FrameLayout>

                <!-- Информация об авторе -->
                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="horizontal"
                    android:gravity="center_vertical"
                    android:padding="16dp"
                    android:background="@color/secondary">

                    <ImageView
                        android:id="@+id/detailAuthorAvatar"
                        android:layout_width="50dp"
                        android:layout_height="50dp"
                        android:src="@drawable/ic_profile_placeholder"
                        android:background="@drawable/circle_background"
                        android:padding="1dp"/>

                    <LinearLayout
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:orientation="vertical"
                        android:layout_marginStart="16dp">

                        <TextView
                            android:id="@+id/detailAuthorName"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Загрузка..."
                            android:textColor="@color/text_primary"
                            android:textStyle="bold"
                            android:textSize="16sp"/>

                        <TextView
                            android:id="@+id/detailDate"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="Дата публикации"
                            android:textColor="@color/text_secondary"
                            android:textSize="14sp"/>
                    </LinearLayout>
                </LinearLayout>

                <LinearLayout
                    android:layout_width="match_parent"
                    android:layout_height="wrap_content"
                    android:orientation="vertical"
                    android:padding="20dp">

                    <!-- Заголовок и лайк -->
                    <LinearLayout
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="top">

                        <TextView
                            android:id="@+id/detailTitle"
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:text="Название"
                            android:textColor="@color/text_primary"
                            android:textSize="24sp"
                            android:textStyle="bold" />

                        <LinearLayout
                            android:id="@+id/detailLikeContainer"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:orientation="vertical"
                            android:gravity="center"
                            android:padding="8dp"
                            android:clickable="true"
                            android:focusable="true"
                            android:background="?attr/selectableItemBackgroundBorderless">

                            <ImageView
                                android:id="@+id/detailLikeIcon"
                                android:layout_width="32dp"
                                android:layout_height="32dp"
                                android:src="@drawable/ic_favorite_border"
                                app:tint="@color/accent"/>

                            <TextView
                                android:id="@+id/detailLikeCount"
                                android:layout_width="wrap_content"
                                android:layout_height="wrap_content"
                                android:text="0"
                                android:textColor="@color/text_primary"
                                android:textSize="14sp"
                                android:textStyle="bold"/>
                        </LinearLayout>
                    </LinearLayout>

                    <!-- Локация -->
                    <LinearLayout
                        android:id="@+id/layoutLocation"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:orientation="horizontal"
                        android:gravity="center_vertical"
                        android:layout_marginTop="8dp"
                        android:layout_marginBottom="16dp"
                        android:background="?attr/selectableItemBackground"
                        android:clickable="true"
                        android:focusable="true"
                        android:padding="8dp">

                        <ImageView
                            android:layout_width="20dp"
                            android:layout_height="20dp"
                            android:src="@drawable/ic_my_location"
                            app:tint="@color/accent"
                            android:layout_marginEnd="8dp"/>

                        <TextView
                            android:id="@+id/detailCoordinates"
                            android:layout_width="wrap_content"
                            android:layout_height="wrap_content"
                            android:text="0.0000, 0.0000"
                            android:textColor="@color/accent"
                            android:textSize="14sp"
                            android:textStyle="bold"/>

                        <TextView
                            android:layout_width="0dp"
                            android:layout_height="wrap_content"
                            android:layout_weight="1"
                            android:gravity="end"
                            android:text="На карте >"
                            android:textSize="12sp"
                            android:alpha="0.7"
                            android:textColor="@color/text_secondary"/>
                    </LinearLayout>

                    <TextView
                        android:layout_width="wrap_content"
                        android:layout_height="wrap_content"
                        android:text="Описание"
                        android:textColor="@color/text_secondary"
                        android:textSize="14sp"
                        android:layout_marginBottom="4dp"/>

                    <TextView
                        android:id="@+id/detailDescription"
                        android:layout_width="match_parent"
                        android:layout_height="wrap_content"
                        android:text="Текст описания..."
                        android:textColor="@color/text_primary"
                        android:textSize="16sp"
                        android:lineSpacingExtra="4dp"/>

                </LinearLayout>
            </LinearLayout>
        </androidx.core.widget.NestedScrollView>
    </androidx.coordinatorlayout.widget.CoordinatorLayout>

    <FrameLayout
        android:id="@+id/expandedContainer"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        android:clickable="true"
        android:focusable="true"
        android:elevation="100dp">

        <View
            android:id="@+id/expandedBackground"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:background="#E6000000"
            android:alpha="0" />

        <ImageView
            android:id="@+id/expandedImage"
            android:layout_width="match_parent"
            android:layout_height="match_parent"
            android:layout_margin="0dp"
            android:scaleType="fitCenter"
            android:src="@color/secondary" />

        <com.google.android.material.button.MaterialButton
            android:id="@+id/btnDownloadExpanded"
            android:layout_width="wrap_content"
            android:layout_height="60dp"
            android:layout_gravity="bottom|center_horizontal"
            android:layout_marginBottom="64dp"
            android:text="Скачать фото"
            android:textColor="@android:color/white"
            app:icon="@drawable/ic_download"
            app:iconTint="@android:color/white"
            app:backgroundTint="@color/secondary"
            app:cornerRadius="30dp"
            android:alpha="0" />

    </FrameLayout>
</FrameLayout>

 */
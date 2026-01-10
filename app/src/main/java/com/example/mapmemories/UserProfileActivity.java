package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
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
import java.util.Map;

public class UserProfileActivity extends AppCompatActivity {

    private String targetUserId;
    private String currentUserId;

    private ImageView profileImage;
    private TextView profileUsername, profileAbout, profileJoinDate, countMemories, countLikes, friendsCount;
    private RecyclerView userPostsRecyclerView;
    private MaterialButton btnFriendAction, btnChat;

    private DatabaseReference userRef, postsRef, rootRef;
    private PublicMemoriesAdapter adapter;
    private List<Post> userPostList;

    private static final String STATE_NOT_FRIENDS = "not_friends";
    private static final String STATE_REQUEST_SENT = "request_sent";
    private static final String STATE_REQUEST_RECEIVED = "request_received";
    private static final String STATE_FRIENDS = "friends";

    private String currentFriendState = STATE_NOT_FRIENDS;

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

        if (currentUserId.equals(targetUserId)) {
            btnFriendAction.setVisibility(View.GONE);
            btnChat.setVisibility(View.GONE);
        } else {
            btnFriendAction.setOnClickListener(v -> performFriendAction());
            btnChat.setOnClickListener(v -> {
                Intent intent = new Intent(UserProfileActivity.this, ChatActivity.class);
                intent.putExtra("targetUserId", targetUserId);
                startActivity(intent);
            });
            checkFriendStatus();
        }

        loadUserInfo();
        setupRecyclerView();
        loadUserPublicPosts();
    }

    private void initViews() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        profileImage = findViewById(R.id.profileImage);
        profileUsername = findViewById(R.id.profileUsername);
        profileAbout = findViewById(R.id.profileAbout);
        profileJoinDate = findViewById(R.id.profileJoinDate);
        countMemories = findViewById(R.id.countMemories);
        countLikes = findViewById(R.id.countLikes);
        friendsCount = findViewById(R.id.friendsCount);
        userPostsRecyclerView = findViewById(R.id.userPostsRecyclerView);

        btnFriendAction = findViewById(R.id.btnFriendAction);
        btnChat = findViewById(R.id.btnChat);
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
                    String imageUrl = snapshot.child("profileImageUrl").getValue(String.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    long friendsCounter = snapshot.child("friends").getChildrenCount();
                    long memCount = snapshot.child("memoriesCount").exists() ? snapshot.child("memoriesCount").getValue(Long.class) : 0;
                    long likesCount = snapshot.child("likesCount").exists() ? snapshot.child("likesCount").getValue(Long.class) : 0;

                    profileUsername.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    profileAbout.setText(TextUtils.isEmpty(about) ? "" : about);
                    profileAbout.setVisibility(TextUtils.isEmpty(about) ? View.GONE : View.VISIBLE);
                    friendsCount.setText(String.valueOf(friendsCounter));
                    countMemories.setText(String.valueOf(memCount));
                    countLikes.setText(String.valueOf(likesCount));

                    if (joinDate != null) {
                        String date = DateFormat.format("dd.MM.yyyy", new Date(joinDate)).toString();
                        profileJoinDate.setText("На сайте с: " + date);
                    }

                    if (!TextUtils.isEmpty(imageUrl)) {
                        Glide.with(UserProfileActivity.this).load(imageUrl).placeholder(R.drawable.ic_profile_placeholder).circleCrop().into(profileImage);
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void checkFriendStatus() {
        rootRef.child("users").child(currentUserId).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isFinishing() || isDestroyed()) return;

                if (snapshot.child("friends").hasChild(targetUserId)) {
                    currentFriendState = STATE_FRIENDS;
                    updateButtonUI("Удалить", R.color.secondary);
                } else if (snapshot.child("requests_sent").hasChild(targetUserId)) {
                    currentFriendState = STATE_REQUEST_SENT;
                    updateButtonUI("Отменить", R.color.secondary);
                } else if (snapshot.child("requests_incoming").hasChild(targetUserId)) {
                    currentFriendState = STATE_REQUEST_RECEIVED;
                    updateButtonUI("Принять", R.color.accent);
                } else {
                    currentFriendState = STATE_NOT_FRIENDS;
                    updateButtonUI("Добавить", R.color.accent);
                }
                btnFriendAction.setEnabled(true);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void updateButtonUI(String text, int colorResId) {
        btnFriendAction.setVisibility(View.VISIBLE);
        btnFriendAction.setText(text);
        btnFriendAction.setBackgroundTintList(getResources().getColorStateList(colorResId));

        if (currentFriendState.equals(STATE_FRIENDS)) {
            btnChat.setVisibility(View.VISIBLE);
        } else {
            btnChat.setVisibility(View.GONE);
        }
    }

    private void performFriendAction() {
        btnFriendAction.setEnabled(false);

        switch (currentFriendState) {
            case STATE_NOT_FRIENDS:
                sendFriendRequest();
                break;
            case STATE_REQUEST_SENT:
                cancelFriendRequest();
                break;
            case STATE_REQUEST_RECEIVED:
                acceptFriendRequest();
                break;
            case STATE_FRIENDS:
                unfriendPerson();
                break;
        }
    }

    private void sendFriendRequest() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/requests_sent/" + targetUserId, true);
        updates.put("users/" + targetUserId + "/requests_incoming/" + currentUserId, true);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing()) {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Заявка отправлена", Toast.LENGTH_SHORT).show();
                } else {
                    btnFriendAction.setEnabled(true);
                    Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void cancelFriendRequest() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/requests_sent/" + targetUserId, null);
        updates.put("users/" + targetUserId + "/requests_incoming/" + currentUserId, null);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing() && !task.isSuccessful()) {
                btnFriendAction.setEnabled(true);
                Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void acceptFriendRequest() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/friends/" + targetUserId, true);
        updates.put("users/" + targetUserId + "/friends/" + currentUserId, true);
        updates.put("users/" + currentUserId + "/requests_incoming/" + targetUserId, null);
        updates.put("users/" + targetUserId + "/requests_sent/" + currentUserId, null);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing()) {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Теперь вы друзья!", Toast.LENGTH_SHORT).show();
                } else {
                    btnFriendAction.setEnabled(true);
                    Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void unfriendPerson() {
        Map<String, Object> updates = new HashMap<>();
        updates.put("users/" + currentUserId + "/friends/" + targetUserId, null);
        updates.put("users/" + targetUserId + "/friends/" + currentUserId, null);
        updates.put("users/" + currentUserId + "/requests_incoming/" + targetUserId, true);
        updates.put("users/" + targetUserId + "/requests_sent/" + currentUserId, true);

        rootRef.updateChildren(updates).addOnCompleteListener(task -> {
            if (!isFinishing()) {
                if (task.isSuccessful()) {
                    Toast.makeText(this, "Пользователь переведен в подписчики", Toast.LENGTH_SHORT).show();
                } else {
                    btnFriendAction.setEnabled(true);
                    Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show();
                }
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
                        if (post != null && post.isPublic()) {
                            userPostList.add(post);
                        }
                    }
                    Collections.reverse(userPostList);
                }
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
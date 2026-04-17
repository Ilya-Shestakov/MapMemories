package com.example.mapmemories.Profile;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.ProgressDialog;
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
import android.text.InputType;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.example.mapmemories.systemHelpers.DialogHelper;
import com.example.mapmemories.Lenta.MemoriesAdapter;
import com.example.mapmemories.Post.Post;
import com.example.mapmemories.Post.PostDetailsActivity;
import com.example.mapmemories.R;
import com.google.android.material.card.MaterialCardView;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;

public class Profile extends AppCompatActivity {

    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;
    private Cloudinary cloudinary;

    private View mainContentLayout;
    private TextView usernameText, emailText, phoneText, aboutText, joinDateText;
    private TextView memoriesCount, likesCount, followersCount, followingCount;
    private LinearLayout followersCountContainer, followingCountContainer;

    private ImageButton editPhoneButton, editAboutButton, editNameButton, btnAddAvatar;
    private RecyclerView memoriesRecyclerView;
    private TextView emptyMemoriesText;

    private ViewPager2 profileImageViewPager;
    private AvatarAdapter smallAvatarAdapter;
    private List<String> avatarUrls = new ArrayList<>();
    private List<String> avatarKeys = new ArrayList<>();

    private CollapsingToolbarLayout collapsingToolbar;

    // Zoom
    private FrameLayout expandedContainer;
    private MaterialCardView expandedCard;
    private ViewPager2 expandedViewPager;
    private AvatarAdapter expandedAvatarAdapter;
    private View expandedBackground;
    private LinearLayout expandedActionsContainer;
    private ImageButton btnShareExpanded, btnDownloadExpanded, btnDeleteExpanded;
    private Animator currentAnimator;

    private ProgressDialog progressDialog;
    private MemoriesAdapter memoriesAdapter;
    private List<Post> myPostList;

    private boolean isClosing = false;
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        initCloudinary();
        myPostList = new ArrayList<>();
        setupImagePicker();

        initViews();
        setupAdapters();
        setupClickListeners();

        loadUserData();
        loadMemories();

        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            mainContentLayout.setVisibility(View.INVISIBLE);
            ViewTreeObserver viewTreeObserver = mainContentLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainContentLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealActivity(getIntent().getIntExtra("revealX", 0),
                                getIntent().getIntExtra("revealY", 0));
                    }
                });
            }
        }
    }

    private void initCloudinary() {
        Map<String, String> config = new HashMap<>();
        config.put("cloud_name", "dvbjhturp");
        config.put("api_key", "149561293632228");
        config.put("api_secret", "U8ZmnwKrLwBxmLbBPMM5CxvEYdU");
        cloudinary = new Cloudinary(config);
    }

    private void setupImagePicker() {
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri != null) uploadImageToCloudinary(imageUri);
                    }
                }
        );
    }

    private void initViews() {
        mainContentLayout = findViewById(R.id.mainContentLayout);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false);
        }
        toolbar.setNavigationOnClickListener(v -> Close());

        usernameText = findViewById(R.id.usernameText);
        emailText = findViewById(R.id.emailText);
        phoneText = findViewById(R.id.phoneText);
        aboutText = findViewById(R.id.aboutText);
        joinDateText = findViewById(R.id.joinDateText);

        collapsingToolbar = findViewById(R.id.collapsingToolbar);

        followersCount = findViewById(R.id.followersCount);
        followingCount = findViewById(R.id.followingCount);
        memoriesCount = findViewById(R.id.memoriesCount);
        likesCount = findViewById(R.id.likesCount);

        followersCountContainer = findViewById(R.id.followersCountContainer);
        followingCountContainer = findViewById(R.id.followingCountContainer);

        profileImageViewPager = findViewById(R.id.profileImageViewPager);
        btnAddAvatar = findViewById(R.id.btnAddAvatar);

        editPhoneButton = findViewById(R.id.editPhoneButton);
        editAboutButton = findViewById(R.id.editAboutButton);
        editNameButton = findViewById(R.id.editNameButton);

        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);

        expandedContainer = findViewById(R.id.expandedContainer);
        expandedCard = findViewById(R.id.expandedCard);
        expandedViewPager = findViewById(R.id.expandedViewPager);
        expandedBackground = findViewById(R.id.expandedBackground);

        expandedActionsContainer = findViewById(R.id.expandedActionsContainer);
        btnShareExpanded = findViewById(R.id.btnShareExpanded);
        btnDownloadExpanded = findViewById(R.id.btnDownloadExpanded);
        btnDeleteExpanded = findViewById(R.id.btnDeleteExpanded);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка...");
        progressDialog.setCancelable(false);
    }

    private void setupAdapters() {
        smallAvatarAdapter = new AvatarAdapter(avatarUrls, position -> {
            if (!avatarUrls.isEmpty()) zoomImageFromThumb(profileImageViewPager, position);
        });
        profileImageViewPager.setAdapter(smallAvatarAdapter);

        expandedAvatarAdapter = new AvatarAdapter(avatarUrls, position -> closeExpandedImage());
        expandedViewPager.setAdapter(expandedAvatarAdapter);
    }

    private void setupClickListeners() {
        editPhoneButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить телефон", phoneText.getText().toString(),
                        InputType.TYPE_CLASS_PHONE, R.drawable.ic_phone,
                        newValue -> updateUserField("phone", newValue)));

        editAboutButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "О себе", aboutText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, R.drawable.ic_info,
                        newValue -> updateUserField("about", newValue)));

        editNameButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить имя", usernameText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, R.drawable.ic_edit,
                        newValue -> updateUserField("username", newValue)));

        btnAddAvatar.setOnClickListener(v -> changeProfileImage());

        followersCountContainer.setOnClickListener(v -> {
            FriendsBottomSheetDialogFragment bottomSheet = FriendsBottomSheetDialogFragment.newInstance("followers", currentUser.getUid());
            bottomSheet.show(getSupportFragmentManager(), "followersSheet");
        });

        followingCountContainer.setOnClickListener(v -> {
            FriendsBottomSheetDialogFragment bottomSheet = FriendsBottomSheetDialogFragment.newInstance("following", currentUser.getUid());
            bottomSheet.show(getSupportFragmentManager(), "followingSheet");
        });

        btnShareExpanded.setOnClickListener(v -> shareCurrentPhoto());
        btnDownloadExpanded.setOnClickListener(v -> downloadCurrentPhoto());
        btnDeleteExpanded.setOnClickListener(v -> deleteCurrentPhoto());
    }

    private void loadUserData() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return;

                if (snapshot.exists()) {
                    if (currentUser.getEmail() != null) emailText.setText(currentUser.getEmail());

                    String username = snapshot.child("username").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);

                    long followers = snapshot.child("requests_incoming").getChildrenCount();
                    long following = snapshot.child("requests_sent").getChildrenCount();

                    followersCount.setText(String.valueOf(followers));
                    followingCount.setText(String.valueOf(following));

                    usernameText.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    phoneText.setText(TextUtils.isEmpty(phone) ? "Не указан" : phone);
                    aboutText.setText(TextUtils.isEmpty(about) ? "Расскажите о себе..." : about);

                    if (joinDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
                        joinDateText.setText(sdf.format(new Date(joinDate)));
                    } else joinDateText.setText("Недавно");

                    avatarUrls.clear();
                    avatarKeys.clear();

                    if (snapshot.hasChild("profileImages")) {
                        for (DataSnapshot imgSnap : snapshot.child("profileImages").getChildren()) {
                            avatarKeys.add(imgSnap.getKey());
                            avatarUrls.add(imgSnap.getValue(String.class));
                        }
                    } else if (snapshot.hasChild("profileImageUrl")) {
                        String oldUrl = snapshot.child("profileImageUrl").getValue(String.class);
                        if (oldUrl != null && !oldUrl.isEmpty()) {
                            avatarKeys.add("legacy_image");
                            avatarUrls.add(oldUrl);
                        }
                    }

                    smallAvatarAdapter.notifyDataSetChanged();
                    expandedAvatarAdapter.notifyDataSetChanged();
                } else createUserProfile();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void createUserProfile() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Пользователь");
        userData.put("email", currentUser.getEmail());
        userData.put("phone", "");
        userData.put("about", "Новый пользователь MapMemories");
        userData.put("profileImageUrl", "");
        userData.put("joinDate", System.currentTimeMillis());
        userData.put("memoriesCount", 0);
        userData.put("likesCount", 0);
        userRef.setValue(userData);
    }

    private void loadMemories() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        memoriesAdapter = new MemoriesAdapter(this, myPostList, post -> {
            Intent intent = new Intent(Profile.this, PostDetailsActivity.class);
            intent.putExtra("postId", post.getId());
            startActivity(intent);
        });
        memoriesRecyclerView.setAdapter(memoriesAdapter);

        if (currentUser == null) return;
        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");

        postsRef.orderByChild("userId").equalTo(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (isDestroyed() || isFinishing()) return;

                        if (myPostList == null) myPostList = new ArrayList<>();
                        myPostList.clear();
                        int totalLikesCounter = 0;

                        if (snapshot.exists()) {
                            for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                                Post post = postSnapshot.getValue(Post.class);
                                if (post != null) {
                                    myPostList.add(post);
                                    if (post.getLikes() != null) totalLikesCounter += post.getLikes().size();
                                }
                            }
                            Collections.reverse(myPostList);
                        }
                        memoriesAdapter.notifyDataSetChanged();

                        memoriesCount.setText(String.valueOf(myPostList.size()));
                        likesCount.setText(String.valueOf(totalLikesCounter));

                        // Получаем параметры шапки
                        AppBarLayout.LayoutParams params = (AppBarLayout.LayoutParams) collapsingToolbar.getLayoutParams();

                        if (myPostList.isEmpty()) {
                            memoriesRecyclerView.setVisibility(View.GONE);

                            // ОТКЛЮЧАЕМ СКРОЛЛ (шапка становится неподвижной)
                            params.setScrollFlags(0);
                        } else {
                            memoriesRecyclerView.setVisibility(View.VISIBLE);

                            // ВКЛЮЧАЕМ СКРОЛЛ обратно (когда есть хотя бы 1 пост)
                            params.setScrollFlags(AppBarLayout.LayoutParams.SCROLL_FLAG_SCROLL | AppBarLayout.LayoutParams.SCROLL_FLAG_EXIT_UNTIL_COLLAPSED);
                        }

                        // Применяем параметры
                        collapsingToolbar.setLayoutParams(params);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void updateUserField(String fieldName, String value) {
        progressDialog.setMessage("Сохранение...");
        progressDialog.show();
        userRef.child(fieldName).setValue(value)
                .addOnSuccessListener(aVoid -> {
                    if (!isFinishing()) {
                        progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Обновлено!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing()) {
                        progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        if (isFinishing() || isDestroyed()) return;
        progressDialog.setMessage("Загрузка фото...");
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(imageUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String imageUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) updateProfileImageUrlInFirebase(imageUrl);
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    if (!isFinishing() && !isDestroyed()) Toast.makeText(Profile.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void updateProfileImageUrlInFirebase(String imageUrl) {
        String newKey = userRef.child("profileImages").push().getKey();
        if (newKey != null) {
            userRef.child("profileImages").child(newKey).setValue(imageUrl);
            userRef.child("profileImageUrl").setValue(imageUrl)
                    .addOnSuccessListener(aVoid -> {
                        progressDialog.dismiss();
                        if (!isFinishing()) Toast.makeText(Profile.this, "Фото добавлено!", Toast.LENGTH_SHORT).show();
                        if(!avatarUrls.isEmpty()) profileImageViewPager.setCurrentItem(avatarUrls.size(), true);
                    })
                    .addOnFailureListener(e -> {
                        progressDialog.dismiss();
                        if (!isFinishing()) Toast.makeText(Profile.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void deleteCurrentPhoto() {
        if (avatarUrls.isEmpty() || avatarKeys.isEmpty()) return;
        int currentIndex = expandedViewPager.getCurrentItem();
        String currentKey = avatarKeys.get(currentIndex);

        new AlertDialog.Builder(this)
                .setTitle("Удалить фото?")
                .setMessage("Вы уверены, что хотите удалить эту фотографию?")
                .setPositiveButton("Удалить", (dialog, which) -> {
                    if (currentKey.equals("legacy_image")) {
                        userRef.child("profileImageUrl").setValue("");
                    } else {
                        userRef.child("profileImages").child(currentKey).removeValue();
                        if (avatarUrls.size() > 1) {
                            int prevIndex = currentIndex > 0 ? currentIndex - 1 : 1;
                            userRef.child("profileImageUrl").setValue(avatarUrls.get(prevIndex));
                        } else {
                            userRef.child("profileImageUrl").setValue("");
                        }
                    }
                    Toast.makeText(this, "Фото удалено", Toast.LENGTH_SHORT).show();
                    if(avatarUrls.size() <= 1) closeExpandedImage();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

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
        else Close();
    }

    public void Close() {
        if (isClosing) return;
        isClosing = true;
        if (getIntent().hasExtra("revealX") && mainContentLayout != null) {
            int revealX = getIntent().getIntExtra("revealX", 0);
            int revealY = getIntent().getIntExtra("revealY", 0);
            unRevealActivity(revealX, revealY);
        } else {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    private void revealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(mainContentLayout.getWidth(), mainContentLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(mainContentLayout, x, y, 0, finalRadius);
        circularReveal.setDuration(400);
        circularReveal.setInterpolator(new AccelerateInterpolator());
        mainContentLayout.setVisibility(View.VISIBLE);
        circularReveal.start();
    }

    private void unRevealActivity(int x, int y) {
        float finalRadius = (float) (Math.max(mainContentLayout.getWidth(), mainContentLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(mainContentLayout, x, y, finalRadius, 0);
        circularReveal.setDuration(400);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mainContentLayout.setVisibility(View.INVISIBLE);
                finish();
                overridePendingTransition(0, 0);
            }
        });
        circularReveal.start();
    }

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
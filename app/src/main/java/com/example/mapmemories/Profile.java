package com.example.mapmemories;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
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

public class Profile extends AppCompatActivity {

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;

    // Cloudinary
    private Cloudinary cloudinary;

    // UI элементы
    private ConstraintLayout rootLayout;
    private ConstraintLayout mainContentLayout;

    private MaterialCardView infoCard, memoriesCard, headerCard;

    private ImageView profileImage;
    private TextView usernameText, emailText, phoneText, aboutText, joinDateText;
    private TextView memoriesCount, placesCount, likesCount;
    private ImageButton buttonBack, editPhoneButton, editAboutButton, editNameButton; // editNameButton вместо editProfileFab
    private RecyclerView memoriesRecyclerView;
    private TextView emptyMemoriesText;
    private ImageButton viewAllMemories;

    // Элементы для анимации Zoom
    private FrameLayout expandedContainer;
    private MaterialCardView expandedCard;
    private ImageView expandedImage;
    private View expandedBackground;
    private ExtendedFloatingActionButton btnEditExpanded;
    private Animator currentAnimator;
    private int shortAnimationDuration;

    private ProgressDialog progressDialog;

    // Список и адаптер
    private MemoriesAdapter memoriesAdapter;
    private List<Post> myPostList;

    private boolean isMemoriesExpanded = false;
    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private String currentProfileImageUrl = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) return;

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        initCloudinary();
        myPostList = new ArrayList<>();
        setupImagePicker();

        initViews();
        setupClickListeners();

        loadUserData();
        loadMemories();

        shortAnimationDuration = getResources().getInteger(android.R.integer.config_shortAnimTime);

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
                        if (imageUri != null) {
                            // !!! МОМЕНТАЛЬНОЕ ОБНОВЛЕНИЕ UI !!!
                            // 1. Сразу ставим картинку в маленький кружок
                            Glide.with(this)
                                    .load(imageUri)
                                    .circleCrop()
                                    .into(profileImage);

                            // 2. Обновляем переменную ссылки (временно на локальный путь),
                            // чтобы если юзер нажмет на аву, она открылась сразу новой
                            currentProfileImageUrl = imageUri.toString();

                            // 3. Запускаем загрузку в облако
                            uploadImageToCloudinary(imageUri);
                        }
                    }
                }
        );
    }

    private void initViews() {
        mainContentLayout = findViewById(R.id.mainContentLayout);
        rootLayout = mainContentLayout;

        headerCard = findViewById(R.id.headerCard);
        infoCard = findViewById(R.id.infoCard);
        memoriesCard = findViewById(R.id.memoriesCard);

        buttonBack = findViewById(R.id.buttonBack);
        profileImage = findViewById(R.id.profileImage);
        usernameText = findViewById(R.id.usernameText);
        emailText = findViewById(R.id.emailText);
        phoneText = findViewById(R.id.phoneText);
        aboutText = findViewById(R.id.aboutText);
        joinDateText = findViewById(R.id.joinDateText);
        memoriesCount = findViewById(R.id.memoriesCount);
        placesCount = findViewById(R.id.placesCount);
        likesCount = findViewById(R.id.likesCount);
        editPhoneButton = findViewById(R.id.editPhoneButton);
        editAboutButton = findViewById(R.id.editAboutButton);

        // !!! НОВАЯ КНОПКА !!!
        editNameButton = findViewById(R.id.editNameButton);

        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        emptyMemoriesText = findViewById(R.id.emptyMemoriesText);
        viewAllMemories = findViewById(R.id.viewAllMemories);

        expandedContainer = findViewById(R.id.expandedContainer);
        expandedCard = findViewById(R.id.expandedCard);
        expandedImage = findViewById(R.id.expandedImage);
        expandedBackground = findViewById(R.id.expandedBackground);
        btnEditExpanded = findViewById(R.id.btnEditExpanded);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        buttonBack.setOnClickListener(v -> Close());

        editPhoneButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить телефон", phoneText.getText().toString(),
                        InputType.TYPE_CLASS_PHONE, R.drawable.ic_phone,
                        newValue -> updateUserField("phone", newValue)));

        editAboutButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "О себе", aboutText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, R.drawable.ic_info,
                        newValue -> updateUserField("about", newValue)));

        // !!! ИЗМЕНЕНО: Редактирование имени по нажатию на карандашик рядом с именем
        editNameButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить имя", usernameText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, R.drawable.ic_edit,
                        newValue -> updateUserField("username", newValue)));

        profileImage.setOnClickListener(v -> {
            if (currentProfileImageUrl != null && !currentProfileImageUrl.isEmpty()) {
                zoomImageFromThumb(profileImage, currentProfileImageUrl);
            } else {
                changeProfileImage();
            }
        });

        btnEditExpanded.setOnClickListener(v -> {
            if (currentAnimator != null) currentAnimator.cancel();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(null);
            }
            expandedContainer.setVisibility(View.GONE);
            changeProfileImage();
        });

        viewAllMemories.setOnClickListener(v -> toggleMemoriesState());
    }

    private void zoomImageFromThumb(final View thumbView, String imageUrl) {
        if (currentAnimator != null) {
            currentAnimator.cancel();
        }

        Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .into(expandedImage);

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

            btnEditExpanded.setAlpha(0f);
            expandedBackground.setAlpha(0f);

            expandedContainer.setAlpha(1f);
            thumbView.setAlpha(0f);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mainContentLayout.setRenderEffect(RenderEffect.createBlurEffect(20f, 20f, Shader.TileMode.MIRROR));
            }

            AnimatorSet set = new AnimatorSet();
            set
                    .play(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_X, 0f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_Y, 0f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_X, 1f))
                    .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_Y, 1f))
                    .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f, 1f))
                    .with(ObjectAnimator.ofFloat(btnEditExpanded, View.ALPHA, 0f, 1f));

            set.setDuration(300);
            set.setInterpolator(new DecelerateInterpolator());
            set.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    currentAnimator = null;
                }
            });
            set.start();
            currentAnimator = set;

            View.OnClickListener closeListener = v -> {
                if (currentAnimator != null) currentAnimator.cancel();

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    mainContentLayout.setRenderEffect(null);
                }

                AnimatorSet closeSet = new AnimatorSet();
                closeSet.play(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_X, startX))
                        .with(ObjectAnimator.ofFloat(expandedCard, View.TRANSLATION_Y, startY))
                        .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_X, startScale))
                        .with(ObjectAnimator.ofFloat(expandedCard, View.SCALE_Y, startScale))
                        .with(ObjectAnimator.ofFloat(expandedBackground, View.ALPHA, 0f))
                        .with(ObjectAnimator.ofFloat(btnEditExpanded, View.ALPHA, 0f));

                closeSet.setDuration(250);
                closeSet.setInterpolator(new AccelerateInterpolator());
                closeSet.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        thumbView.setAlpha(1f);
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
            };

            expandedImage.setOnClickListener(closeListener);
            expandedBackground.setOnClickListener(closeListener);
        });
    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        if (isFinishing() || isDestroyed()) return;
        // !!! НЕ показываем диалог, так как картинка уже на экране
        // progressDialog.setMessage("Загрузка фото...");
        // progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream inputStream = null;
            try {
                inputStream = getContentResolver().openInputStream(imageUri);
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String imageUrl = (String) uploadResult.get("secure_url");

                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        updateProfileImageUrlInFirebase(imageUrl);
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        // progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                if (inputStream != null) {
                    try { inputStream.close(); } catch (Exception e) { e.printStackTrace(); }
                }
            }
        });
    }

    private void updateProfileImageUrlInFirebase(String imageUrl) {
        userRef.child("profileImageUrl").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    // if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Фото сохранено!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    // if (progressDialog.isShowing()) progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserData() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return;

                if (snapshot.exists()) {
                    if (currentUser.getEmail() != null) {
                        emailText.setText(currentUser.getEmail());
                    }

                    String username = snapshot.child("username").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);

                    String remoteImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);
                    Long memories = snapshot.child("memoriesCount").getValue(Long.class);
                    Long places = snapshot.child("placesCount").getValue(Long.class);
                    Long likes = snapshot.child("likesCount").getValue(Long.class);

                    usernameText.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    phoneText.setText(TextUtils.isEmpty(phone) ? "Не указан" : phone);
                    aboutText.setText(TextUtils.isEmpty(about) ? "Расскажите о себе..." : about);

                    memoriesCount.setText(String.valueOf(memories != null ? memories : 0));
                    placesCount.setText(String.valueOf(places != null ? places : 0));
                    likesCount.setText(String.valueOf(likes != null ? likes : 0));

                    if (joinDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
                        joinDateText.setText(sdf.format(new Date(joinDate)));
                    } else {
                        joinDateText.setText("Недавно");
                    }

                    // Обновляем ссылку, но картинку грузим только если она изменилась на сервере,
                    // чтобы не моргало после нашей локальной установки
                    if (remoteImageUrl != null && !remoteImageUrl.equals(currentProfileImageUrl)) {
                        currentProfileImageUrl = remoteImageUrl;
                        Glide.with(Profile.this)
                                .load(currentProfileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(profileImage);
                    } else if (currentProfileImageUrl == null && remoteImageUrl != null) {
                        // Первый запуск
                        currentProfileImageUrl = remoteImageUrl;
                        Glide.with(Profile.this)
                                .load(currentProfileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .circleCrop()
                                .into(profileImage);
                    }

                } else {
                    createUserProfile();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isDestroyed() && !isFinishing()) {
                    Toast.makeText(Profile.this, "Ошибка: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    // ... Остальные методы (createUserProfile, loadMemories, updateMemoriesCountUI, updateUserField, onBackPressed, Close, revealActivity, unRevealActivity, toggleMemoriesState) без изменений ...

    // (Копируй их из предыдущего ответа, если нужно, но они не менялись)
    private void createUserProfile() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", currentUser.getDisplayName() != null ? currentUser.getDisplayName() : "Пользователь");
        userData.put("email", currentUser.getEmail());
        userData.put("phone", "");
        userData.put("about", "Новый пользователь MapMemories");
        userData.put("profileImageUrl", "");
        userData.put("joinDate", System.currentTimeMillis());
        userData.put("memoriesCount", 0);
        userData.put("placesCount", 0);
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
                        if (myPostList == null) myPostList = new ArrayList<>();
                        myPostList.clear();
                        if (snapshot.exists()) {
                            for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                                Post post = postSnapshot.getValue(Post.class);
                                if (post != null) {
                                    myPostList.add(post);
                                }
                            }
                            Collections.reverse(myPostList);
                        }
                        memoriesAdapter.notifyDataSetChanged();
                        updateMemoriesCountUI();
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {
                        Toast.makeText(Profile.this, "Ошибка загрузки постов", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void updateMemoriesCountUI() {
        if (myPostList.isEmpty()) {
            emptyMemoriesText.setVisibility(View.VISIBLE);
            memoriesRecyclerView.setVisibility(View.GONE);
        } else {
            emptyMemoriesText.setVisibility(View.GONE);
            memoriesRecyclerView.setVisibility(View.VISIBLE);
        }
        memoriesCount.setText(String.valueOf(myPostList.size()));
        userRef.child("memoriesCount").setValue(myPostList.size());
    }

    private void updateUserField(String fieldName, String value) {
        progressDialog.setMessage("Сохранение...");
        progressDialog.show();
        userRef.child(fieldName).setValue(value)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Обновлено!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Ошибка: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    @Override
    public void onBackPressed() {
        if (expandedContainer.getVisibility() == View.VISIBLE) {
            expandedImage.performClick();
        } else if (isMemoriesExpanded) {
            toggleMemoriesState();
        } else if (getIntent().hasExtra("revealX")) {
            int revealX = getIntent().getIntExtra("revealX", 0);
            int revealY = getIntent().getIntExtra("revealY", 0);
            unRevealActivity(revealX, revealY);
        } else {
            super.onBackPressed();
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    public void Close() {
        int revealX = getIntent().getIntExtra("revealX", 0);
        int revealY = getIntent().getIntExtra("revealY", 0);
        unRevealActivity(revealX, revealY);
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

        circularReveal.setDuration(500);
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

    private void toggleMemoriesState() {
        isMemoriesExpanded = !isMemoriesExpanded;
        ConstraintSet constraintSet = new ConstraintSet();
        constraintSet.clone(rootLayout);

        AutoTransition transition = new AutoTransition();
        transition.setDuration(500);
        TransitionManager.beginDelayedTransition(rootLayout, transition);

        if (isMemoriesExpanded) {
            infoCard.setVisibility(View.GONE);
            // editProfileFab.hide(); // Удалили эту кнопку
            constraintSet.connect(R.id.memoriesCard, ConstraintSet.TOP, R.id.headerCard, ConstraintSet.BOTTOM);
            viewAllMemories.animate().rotation(180f).setDuration(400).start();
        } else {
            infoCard.setVisibility(View.VISIBLE);
            // editProfileFab.show(); // Удалили эту кнопку
            constraintSet.connect(R.id.memoriesCard, ConstraintSet.TOP, R.id.infoCard, ConstraintSet.BOTTOM);
            viewAllMemories.animate().rotation(0f).setDuration(400).start();
        }
        constraintSet.applyTo(rootLayout);
    }
}
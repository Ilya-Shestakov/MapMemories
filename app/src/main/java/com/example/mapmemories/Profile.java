package com.example.mapmemories;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager; // Важный импорт
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
import java.util.ArrayList; // Важный импорт
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
    private ImageView profileImage;
    private TextView usernameText, emailText, phoneText, aboutText, joinDateText;
    private TextView memoriesCount, placesCount, likesCount;
    private ImageButton buttonBack, editPhoneButton, editAboutButton;
    private ExtendedFloatingActionButton editProfileFab;
    private RecyclerView memoriesRecyclerView;
    private TextView emptyMemoriesText;
    private ImageButton viewAllMemories;

    private ProgressDialog progressDialog;

    // Список и адаптер
    private MemoriesAdapter memoriesAdapter;
    private List<Post> myPostList;

    // Лаунчер для выбора фото из галереи
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // 1. Инициализация Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            return; // Или перенаправить на экран входа
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        // 2. Инициализация Cloudinary
        initCloudinary();

        // 3. Инициализация списка (ИСПРАВЛЕНИЕ ВЫЛЕТА)
        myPostList = new ArrayList<>();

        // 4. Настройка лаунчера
        setupImagePicker();

        // UI
        initViews();
        setupClickListeners();

        loadUserData();
        loadMemories();
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
                            uploadImageToCloudinary(imageUri);
                        }
                    }
                }
        );
    }

    private void initViews() {
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
        editProfileFab = findViewById(R.id.editProfileFab);
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        emptyMemoriesText = findViewById(R.id.emptyMemoriesText);
        viewAllMemories = findViewById(R.id.viewAllMemories);

        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        buttonBack.setOnClickListener(v -> Close());

        editPhoneButton.setOnClickListener(v -> showEditDialog("phone", "Изменить телефон", phoneText.getText().toString(), InputType.TYPE_CLASS_PHONE));
        editAboutButton.setOnClickListener(v -> showEditDialog("about", "О себе", aboutText.getText().toString(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));
        editProfileFab.setOnClickListener(v -> showEditDialog("username", "Изменить имя", usernameText.getText().toString(), InputType.TYPE_CLASS_TEXT));

        profileImage.setOnClickListener(v -> changeProfileImage());

        viewAllMemories.setOnClickListener(v -> {
            Intent intent = new Intent(Profile.this, AllMemoriesActivity.class);
            startActivity(intent);
        });

    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        progressDialog.setMessage("Загрузка фото...");
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Map uploadResult = cloudinary.uploader().upload(inputStream, ObjectUtils.emptyMap());
                String imageUrl = (String) uploadResult.get("secure_url");
                runOnUiThread(() -> updateProfileImageUrlInFirebase(imageUrl));
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void updateProfileImageUrlInFirebase(String imageUrl) {
        userRef.child("profileImageUrl").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Фото обновлено!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    progressDialog.dismiss();
                    Toast.makeText(Profile.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void loadUserData() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (isDestroyed() || isFinishing()) return; // Защита от краша Glide

                if (snapshot.exists()) {
                    emailText.setText(currentUser.getEmail());
                    String username = snapshot.child("username").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);
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

                    if (!TextUtils.isEmpty(profileImageUrl)) {
                        Glide.with(Profile.this)
                                .load(profileImageUrl)
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
                Toast.makeText(Profile.this, "Ошибка загрузки: " + error.getMessage(), Toast.LENGTH_SHORT).show();
            }
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
        userData.put("placesCount", 0);
        userData.put("likesCount", 0);
        userRef.setValue(userData);
    }

    private void loadMemories() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // Инициализируем адаптер с обработчиком клика
        memoriesAdapter = new MemoriesAdapter(this, myPostList, new MemoriesAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(Post post) {
                // Переход на экран деталей при клике на элемент списка в профиле
                Intent intent = new Intent(Profile.this, PostDetailsActivity.class);
                intent.putExtra("postId", post.getId());
                startActivity(intent);
            }
        });

        memoriesRecyclerView.setAdapter(memoriesAdapter);

        if (currentUser == null) return;

        DatabaseReference postsRef = FirebaseDatabase.getInstance().getReference("posts");

        // Ограничиваем загрузку последними 3-5 постами для профиля, если хочешь,
        // или грузим все, но показываем в маленьком окне.
        // В данном коде грузятся все посты юзера.
        postsRef.orderByChild("userId").equalTo(currentUser.getUid())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        // Проверка на null, чтобы избежать краша при быстром выходе
                        if (myPostList == null) myPostList = new ArrayList<>();

                        myPostList.clear();

                        if (snapshot.exists()) {
                            for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                                Post post = postSnapshot.getValue(Post.class);
                                if (post != null) {
                                    myPostList.add(post);
                                }
                            }
                            Collections.reverse(myPostList); // Сначала новые
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

    private void showEditDialog(String fieldKey, String title, String currentValue, int inputType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);
        final EditText input = new EditText(this);
        input.setInputType(inputType);
        if (fieldKey.equals("about")) {
            input.setLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        if (!currentValue.equals("Не указан") && !currentValue.equals("Расскажите о себе...") && !currentValue.equals("Пользователь")) {
            input.setText(currentValue);
        }
        input.setPadding(50, 30, 50, 30);
        builder.setView(input);
        builder.setPositiveButton("Сохранить", (dialog, which) -> {
            String newValue = input.getText().toString().trim();
            updateUserField(fieldKey, newValue);
        });
        builder.setNegativeButton("Отмена", (dialog, which) -> dialog.cancel());
        builder.show();
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
        super.onBackPressed();
        Close();
    }

    private void Close() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
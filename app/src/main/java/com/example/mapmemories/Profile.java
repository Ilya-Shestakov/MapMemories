package com.example.mapmemories;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.transition.AutoTransition;
import android.transition.TransitionManager;
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
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
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
    private ConstraintLayout rootLayout; // Корневой контейнер
    private MaterialCardView infoCard, memoriesCard, headerCard;

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

    // Состояние развернутости блока воспоминаний
    private boolean isMemoriesExpanded = false;

    // Лаунчер для выбора фото из галереи
    private ActivityResultLauncher<Intent> imagePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());

        initCloudinary();
        myPostList = new ArrayList<>();
        setupImagePicker();

        initViews();
        setupClickListeners();

        loadUserData();
        loadMemories();
    }

    private void initCloudinary() {



        /// ***Cloudinary***


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
        // Находим основные контейнеры для анимации
        rootLayout = findViewById(R.id.rootLayout);
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

        editPhoneButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить телефон", phoneText.getText().toString(),
                        InputType.TYPE_CLASS_PHONE, R.drawable.ic_phone,
                        newValue -> updateUserField("phone", newValue)));

        editAboutButton.setOnClickListener(v ->
                DialogHelper.showInput(this, "О себе", aboutText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE, R.drawable.ic_info,
                        newValue -> updateUserField("about", newValue)));

        editProfileFab.setOnClickListener(v ->
                DialogHelper.showInput(this, "Изменить имя", usernameText.getText().toString(),
                        InputType.TYPE_CLASS_TEXT, R.drawable.ic_edit,
                        newValue -> updateUserField("username", newValue)));

        profileImage.setOnClickListener(v -> changeProfileImage());


        viewAllMemories.setOnClickListener(v -> toggleMemoriesState());
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
            editProfileFab.hide();

            constraintSet.connect(R.id.memoriesCard, ConstraintSet.TOP, R.id.headerCard, ConstraintSet.BOTTOM);

            viewAllMemories.animate().rotation(180f).setDuration(400).start();

        } else {

            infoCard.setVisibility(View.VISIBLE);
            editProfileFab.show();

            constraintSet.connect(R.id.memoriesCard, ConstraintSet.TOP, R.id.infoCard, ConstraintSet.BOTTOM);

            viewAllMemories.animate().rotation(0f).setDuration(400).start();
        }

        constraintSet.applyTo(rootLayout);
    }

    private void changeProfileImage() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        imagePickerLauncher.launch(intent);
    }

    private void uploadImageToCloudinary(Uri imageUri) {
        // Проверка перед показом диалога
        if (isFinishing() || isDestroyed()) return;

        progressDialog.setMessage("Загрузка фото...");
        progressDialog.show();

        Executors.newSingleThreadExecutor().execute(() -> {
            InputStream inputStream = null;
            try {
                // Открываем поток
                inputStream = getContentResolver().openInputStream(imageUri);

                // Настройки загрузки Cloudinary (сжатие на сервере для экономии трафика)
                Map<String, Object> options = new HashMap<>();
                options.put("resource_type", "image");
                // Можно добавить трансформацию, чтобы хранить сразу квадратные аватарки 500x500
                // options.put("transformation", new com.cloudinary.Transformation().width(500).height(500).crop("fill"));

                Map uploadResult = cloudinary.uploader().upload(inputStream, options);
                String imageUrl = (String) uploadResult.get("secure_url");

                // Обновляем UI в главном потоке
                runOnUiThread(() -> {
                    // Еще одна проверка, жива ли активити
                    if (!isFinishing() && !isDestroyed()) {
                        updateProfileImageUrlInFirebase(imageUrl);
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Ошибка загрузки: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            } finally {
                // ОБЯЗАТЕЛЬНО закрываем поток, чтобы избежать утечек
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });
    }

    private void updateProfileImageUrlInFirebase(String imageUrl) {
        userRef.child("profileImageUrl").setValue(imageUrl)
                .addOnSuccessListener(aVoid -> {
                    // Проверяем состояние Activity перед закрытием диалога
                    if (!isFinishing() && !isDestroyed() && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Фото обновлено!", Toast.LENGTH_SHORT).show();
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isFinishing() && !isDestroyed() && progressDialog.isShowing()) {
                        progressDialog.dismiss();
                        Toast.makeText(Profile.this, "Ошибка сохранения: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void loadUserData() {
        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                // --- ГЛАВНАЯ ЗАЩИТА ОТ ВЫЛЕТА ---
                // Если активити уже закрывается или уничтожена, прерываем выполнение.
                // Иначе Glide попытается отрисовать картинку на "мертвом" экране и будет краш.
                if (isDestroyed() || isFinishing()) {
                    return;
                }

                if (snapshot.exists()) {
                    // 1. Устанавливаем Email (он берется из авторизации, а не из базы)
                    if (currentUser.getEmail() != null) {
                        emailText.setText(currentUser.getEmail());
                    }

                    // 2. Достаем данные из снимка базы данных
                    String username = snapshot.child("username").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);

                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);
                    Long memories = snapshot.child("memoriesCount").getValue(Long.class);
                    Long places = snapshot.child("placesCount").getValue(Long.class);
                    Long likes = snapshot.child("likesCount").getValue(Long.class);

                    // 3. Устанавливаем тексты (с проверкой на пустоту)
                    usernameText.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    phoneText.setText(TextUtils.isEmpty(phone) ? "Не указан" : phone);
                    aboutText.setText(TextUtils.isEmpty(about) ? "Расскажите о себе..." : about);

                    // 4. Устанавливаем счетчики (с защитой от null)
                    memoriesCount.setText(String.valueOf(memories != null ? memories : 0));
                    placesCount.setText(String.valueOf(places != null ? places : 0));
                    likesCount.setText(String.valueOf(likes != null ? likes : 0));

                    // 5. Форматируем дату регистрации
                    if (joinDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
                        joinDateText.setText(sdf.format(new Date(joinDate)));
                    } else {
                        joinDateText.setText("Недавно");
                    }

                    // 6. Загружаем аватарку через Glide
                    if (!TextUtils.isEmpty(profileImageUrl)) {
                        Glide.with(Profile.this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder) // Картинка пока грузится
                                .error(R.drawable.ic_profile_placeholder)       // Картинка если ошибка
                                .circleCrop()                                   // Круглая обрезка
                                .into(profileImage);
                    } else {
                        // Если ссылки нет совсем, ставим заглушку
                        profileImage.setImageResource(R.drawable.ic_profile_placeholder);
                    }

                } else {
                    // Если записи пользователя в базе нет, создаем её
                    createUserProfile();
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Тут тоже проверка, чтобы Toast не крашнул приложение при закрытии
                if (!isDestroyed() && !isFinishing()) {
                    Toast.makeText(Profile.this, "Ошибка загрузки данных: " + error.getMessage(), Toast.LENGTH_SHORT).show();
                }
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

        memoriesAdapter = new MemoriesAdapter(this, myPostList, new MemoriesAdapter.OnPostClickListener() {
            @Override
            public void onPostClick(Post post) {
                Intent intent = new Intent(Profile.this, PostDetailsActivity.class);
                intent.putExtra("postId", post.getId());
                startActivity(intent);
            }
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
        if (isMemoriesExpanded) {
            toggleMemoriesState();
        } else {
            super.onBackPressed();
            Close();
        }
    }

    private void Close() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
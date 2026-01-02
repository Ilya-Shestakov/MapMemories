package com.example.mapmemories;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
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

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class Profile extends AppCompatActivity {

    // Firebase
    private FirebaseAuth mAuth;
    private FirebaseUser currentUser;
    private DatabaseReference userRef;
    private StorageReference storageRef;

    // UI элементы
    private ImageView profileImage;
    private TextView usernameText, emailText, phoneText, aboutText, joinDateText;
    private TextView memoriesCount, placesCount, likesCount;
    private ImageButton buttonBack, editPhoneButton, editAboutButton;
    private ExtendedFloatingActionButton editProfileFab;
    // private MaterialButton viewAllButton; // УДАЛЕНО: Этой кнопки нет в XML
    private RecyclerView memoriesRecyclerView;
    private TextView emptyMemoriesText;

    private ProgressDialog progressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Firebase
        mAuth = FirebaseAuth.getInstance();
        currentUser = mAuth.getCurrentUser();

        if (currentUser == null) {
            // Если пользователь не авторизован, выкидываем на экран входа
            // startActivity(new Intent(this, LoginActivity.class));
            // finish();
            return;
        }

        userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        storageRef = FirebaseStorage.getInstance().getReference("profile_images");

        // UI
        initViews();
        setupClickListeners();

        loadUserData();
        loadMemories();
    }

    private void initViews() {
        // Находим все View элементы
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

        // viewAllButton = findViewById(R.id.viewAllButton); // УДАЛЕНО

        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        emptyMemoriesText = findViewById(R.id.emptyMemoriesText);

        // Настройка ProgressDialog
        progressDialog = new ProgressDialog(this);
        progressDialog.setMessage("Загрузка...");
        progressDialog.setCancelable(false);
    }

    private void setupClickListeners() {
        // Кнопка назад
        buttonBack.setOnClickListener(v -> Close());

        // Кнопка редактирования телефона (маленький карандаш)
        editPhoneButton.setOnClickListener(v -> showEditDialog("phone", "Изменить телефон", phoneText.getText().toString(), InputType.TYPE_CLASS_PHONE));

        // Кнопка редактирования информации (маленький карандаш)
        editAboutButton.setOnClickListener(v -> showEditDialog("about", "О себе", aboutText.getText().toString(), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE));

        // FAB (Большая кнопка "Редактировать")
        // Теперь она меняет Имя пользователя, так как отдельного экрана EditProfileActivity нет
        editProfileFab.setOnClickListener(v -> {
            showEditDialog("username", "Изменить имя", usernameText.getText().toString(), InputType.TYPE_CLASS_TEXT);
        });

        // Клик на аватарку для изменения фото
        profileImage.setOnClickListener(v -> changeProfileImage());
    }

    private void loadUserData() {
        progressDialog.show();

        userRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    // Базовые данные из Firebase Auth
                    emailText.setText(currentUser.getEmail());

                    // Данные из Realtime Database
                    String username = snapshot.child("username").getValue(String.class);
                    String phone = snapshot.child("phone").getValue(String.class);
                    String about = snapshot.child("about").getValue(String.class);
                    String profileImageUrl = snapshot.child("profileImageUrl").getValue(String.class);
                    Long joinDate = snapshot.child("joinDate").getValue(Long.class);
                    Long memories = snapshot.child("memoriesCount").getValue(Long.class);
                    Long places = snapshot.child("placesCount").getValue(Long.class);
                    Long likes = snapshot.child("likesCount").getValue(Long.class);

                    // Устанавливаем значения
                    usernameText.setText(TextUtils.isEmpty(username) ? "Пользователь" : username);
                    phoneText.setText(TextUtils.isEmpty(phone) ? "Не указан" : phone);
                    aboutText.setText(TextUtils.isEmpty(about) ? "Расскажите о себе..." : about);

                    // Статистика
                    memoriesCount.setText(String.valueOf(memories != null ? memories : 0));
                    placesCount.setText(String.valueOf(places != null ? places : 0));
                    likesCount.setText(String.valueOf(likes != null ? likes : 0));

                    // Дата регистрации
                    if (joinDate != null) {
                        SimpleDateFormat sdf = new SimpleDateFormat("d MMMM yyyy", new Locale("ru"));
                        joinDateText.setText(sdf.format(new Date(joinDate)));
                    } else {
                        joinDateText.setText("Недавно");
                    }

                    // Загрузка изображения профиля
                    if (!TextUtils.isEmpty(profileImageUrl)) {
                        Glide.with(Profile.this)
                                .load(profileImageUrl)
                                .placeholder(R.drawable.ic_profile_placeholder)
                                .into(profileImage);
                    }
                } else {
                    // Если записи нет, создаем её
                    createUserProfile();
                }
                progressDialog.dismiss();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                progressDialog.dismiss();
                Toast.makeText(Profile.this, "Ошибка загрузки: " + error.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void createUserProfile() {
        Map<String, Object> userData = new HashMap<>();
        userData.put("username", currentUser.getDisplayName() != null ?
                currentUser.getDisplayName() : "Пользователь");
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
        // Временно просто показываем текст, что пусто
        if (emptyMemoriesText != null) {
            emptyMemoriesText.setVisibility(View.VISIBLE);
        }
    }

    // Я объединил два метода диалогов в один универсальный, чтобы кода было меньше
    private void showEditDialog(String fieldKey, String title, String currentValue, int inputType) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title);

        final EditText input = new EditText(this);
        input.setInputType(inputType);

        // Если это поле "О себе", делаем его многострочным
        if (fieldKey.equals("about")) {
            input.setLines(3);
            input.setGravity(Gravity.TOP | Gravity.START);
        }

        // Убираем дефолтные тексты-заглушки при редактировании
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

    private void changeProfileImage() {
        Toast.makeText(this, "Функция смены фото будет позже", Toast.LENGTH_SHORT).show();
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
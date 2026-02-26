package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;

public class Setting extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnEditProfile, btnChangePassword, btnClearCache, btnLogout;
    private SwitchMaterial switchNotifications;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        initViews();
        setupClickListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnLogout = findViewById(R.id.btnLogout);
        switchNotifications = findViewById(R.id.switchNotifications);
    }

    private void setupClickListeners() {
        // Кнопка Назад
        btnBack.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            finish();
            overridePendingTransition(0, 0); // Убираем стандартную анимацию закрытия
        });

        // Редактировать профиль
        btnEditProfile.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            // startActivity(new Intent(this, EditProfileActivity.class));
            Toast.makeText(this, "Редактирование профиля", Toast.LENGTH_SHORT).show();
        });

        // Смена пароля
        btnChangePassword.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Toast.makeText(this, "Смена пароля", Toast.LENGTH_SHORT).show();
        });

        // Очистка кэша
        btnClearCache.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Toast.makeText(this, "Кэш успешно очищен", Toast.LENGTH_SHORT).show();
        });

        // Уведомления
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            VibratorHelper.vibrate(this, 30);
            if (isChecked) {
                Toast.makeText(this, "Уведомления включены", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Уведомления выключены", Toast.LENGTH_SHORT).show();
            }
        });

        // Выход из аккаунта (Перенесли сюда из MainActivity)
        btnLogout.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            DialogHelper.showConfirmation(this, "Выход", "Вы уверены, что хотите выйти из аккаунта?", () -> {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(Setting.this, LoginActivity.class);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            });
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}
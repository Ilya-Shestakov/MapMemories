package com.example.mapmemories.Settings;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mapmemories.LogRegStart.LoginActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.DialogHelper;
import com.example.mapmemories.systemHelpers.MessageListenerService;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import java.io.File;

public class Setting extends AppCompatActivity {

    private ImageView btnBack;
    private TextView btnChangePassword, btnPrivacy, btnMapStyle, btnClearCache, btnSupport, btnLogout, btnDeleteAccount;
    private SwitchMaterial switchTheme, switchNotifications, switchSavePhotos;

    private SharedPreferences prefs;
    private static final String PREFS_NAME = "AppPrefs";
    private static final String PREF_NOTIFICATIONS = "notifications_enabled";
    private static final String PREF_DARK_THEME = "dark_theme_enabled";
    private static final String PREF_SAVE_PHOTOS = "save_photos_enabled";
    private static final int NOTIFICATION_PERMISSION_CODE = 123;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

        initViews();
        loadSettings();
        setupClickListeners();
    }

    private void initViews() {
        btnBack = findViewById(R.id.btnBack);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnPrivacy = findViewById(R.id.btnPrivacy);
        btnMapStyle = findViewById(R.id.btnMapStyle);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnSupport = findViewById(R.id.btnSupport);
        btnLogout = findViewById(R.id.btnLogout);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        switchTheme = findViewById(R.id.switchTheme);
        switchNotifications = findViewById(R.id.switchNotifications);
        switchSavePhotos = findViewById(R.id.switchSavePhotos);
    }

    private void loadSettings() {
        switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS, true));
        switchTheme.setChecked(prefs.getBoolean(PREF_DARK_THEME, true)); // По умолчанию темная
        switchSavePhotos.setChecked(prefs.getBoolean(PREF_SAVE_PHOTOS, false));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            finish();
            overridePendingTransition(0, 0);
        });

        // --- АККАУНТ ---
        btnChangePassword.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            sendPasswordResetEmail();
        });

        btnPrivacy.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Toast.makeText(this, "Настройки приватности в разработке", Toast.LENGTH_SHORT).show();
        });

        // --- ОТОБРАЖЕНИЕ ---
        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                prefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
                // Применяем тему (требует настройки themes.xml в проекте)
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        btnMapStyle.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            showMapStyleDialog();
        });

        // --- ПРИЛОЖЕНИЕ ---
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                handleNotificationsToggle(isChecked);
            }
        });

        switchSavePhotos.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                prefs.edit().putBoolean(PREF_SAVE_PHOTOS, isChecked).apply();
            }
        });

        btnClearCache.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            clearAppCache();
        });

        // --- ПОДДЕРЖКА ---
        btnSupport.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            contactSupport();
        });

        // --- ОПАСНАЯ ЗОНА ---
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

        btnDeleteAccount.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 100);
            showDeleteAccountDialog();
        });
    }

    // --- ЛОГИКА ФУНКЦИЙ ---

    private void sendPasswordResetEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail())
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Toast.makeText(this, "Письмо отправлено на " + user.getEmail(), Toast.LENGTH_LONG).show();
                        } else {
                            Toast.makeText(this, "Ошибка при отправке письма", Toast.LENGTH_SHORT).show();
                        }
                    });
        }
    }

    private void showMapStyleDialog() {
        String[] styles = {"Стандартная", "Спутник", "Рельеф", "Темная (Ночь)"};
        int checkedItem = 0; // Здесь можно брать сохраненное значение из prefs

        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories)
                .setTitle("Стиль карты")
                .setSingleChoiceItems(styles, checkedItem, (dialog, which) -> {
                    // TODO: Сохранить выбранный стиль в SharedPreferences
                    Toast.makeText(this, "Выбран стиль: " + styles[which], Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void contactSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:")); // Только почтовые приложения
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"shvi.coffein@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "MapMemories: Обращение в поддержку");

        try {
            startActivity(Intent.createChooser(intent, "Написать разработчикам"));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "Нет установленных почтовых клиентов", Toast.LENGTH_SHORT).show();
        }
    }

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories)
                .setTitle("Удаление аккаунта")
                .setMessage("Это действие необратимо. Все ваши воспоминания, фото и чаты будут удалены навсегда. Вы уверены?")
                .setPositiveButton("Удалить навсегда", (dialog, which) -> {
                    // Заглушка. Для реального удаления нужно re-authenticate пользователя
                    Toast.makeText(this, "Функция удаления в разработке", Toast.LENGTH_LONG).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void clearAppCache() {
        try {
            File dir = getCacheDir();
            if (deleteDir(dir)) {
                Toast.makeText(this, "Кэш успешно очищен", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Кэш уже пуст", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "Ошибка очистки кэша", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) {
                for (String child : children) {
                    boolean success = deleteDir(new File(dir, child));
                    if (!success) return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        }
        return false;
    }

    private void handleNotificationsToggle(boolean isChecked) {
        if (isChecked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION_CODE);
                    return;
                }
            }
            enableNotifications();
        } else {
            disableNotifications();
        }
    }

    private void enableNotifications() {
        prefs.edit().putBoolean(PREF_NOTIFICATIONS, true).apply();
        startService(new Intent(this, MessageListenerService.class));
    }

    private void disableNotifications() {
        prefs.edit().putBoolean(PREF_NOTIFICATIONS, false).apply();
        stopService(new Intent(this, MessageListenerService.class));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == NOTIFICATION_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                enableNotifications();
            } else {
                switchNotifications.setChecked(false);
                Toast.makeText(this, "Необходимо разрешение для уведомлений", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, 0);
    }
}
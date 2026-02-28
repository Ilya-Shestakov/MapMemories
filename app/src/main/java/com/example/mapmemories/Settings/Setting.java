package com.example.mapmemories.Settings;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.animation.AccelerateInterpolator;
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
import com.example.mapmemories.systemHelpers.SwipeBackHelper;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.FirebaseDatabase;

import java.io.File;

public class Setting extends AppCompatActivity {

    private View mainContentLayout;
    private SwipeBackHelper swipeBackHelper;

    private ImageView btnBack;
    private TextView btnChangePassword, btnPrivacy, btnTextSize, btnClearCache, btnSupport, btnLogout, btnDeleteAccount;
    private SwitchMaterial switchTheme, switchNotifications;

    private SharedPreferences prefs;
    public static final String PREFS_NAME = "AppPrefs";
    private static final String PREF_NOTIFICATIONS = "notifications_enabled";
    private static final String PREF_DARK_THEME = "dark_theme_enabled";
    public static final String PREF_TEXT_SCALE = "text_scale";

    // Ключи для приватности
    private static final String PREF_PRIVACY_CLOSED_PROFILE = "privacy_closed_profile";
    private static final String PREF_PRIVACY_HIDE_SEARCH = "privacy_hide_search";
    private static final String PREF_PRIVACY_HIDE_ONLINE = "privacy_hide_online";

    private static final int NOTIFICATION_PERMISSION_CODE = 123;

    @Override
    protected void attachBaseContext(Context newBase) {
        SharedPreferences preferences = newBase.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        float scale = preferences.getFloat(PREF_TEXT_SCALE, 1.0f);

        Configuration config = new Configuration(newBase.getResources().getConfiguration());
        config.fontScale = scale;
        Context context = newBase.createConfigurationContext(config);
        super.attachBaseContext(context);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setting);

        prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        swipeBackHelper = new SwipeBackHelper(this);

        initViews();
        loadSettings();
        setupClickListeners();
        handleRevealAnimation(savedInstanceState);
    }

    private void initViews() {
        mainContentLayout = findViewById(R.id.mainContentLayout);
        btnBack = findViewById(R.id.btnBack);
        btnChangePassword = findViewById(R.id.btnChangePassword);
        btnPrivacy = findViewById(R.id.btnPrivacy);
        btnTextSize = findViewById(R.id.btnTextSize);
        btnClearCache = findViewById(R.id.btnClearCache);
        btnSupport = findViewById(R.id.btnSupport);
        btnLogout = findViewById(R.id.btnLogout);
        btnDeleteAccount = findViewById(R.id.btnDeleteAccount);

        switchTheme = findViewById(R.id.switchTheme);
        switchNotifications = findViewById(R.id.switchNotifications);
    }

    private void loadSettings() {
        switchNotifications.setChecked(prefs.getBoolean(PREF_NOTIFICATIONS, true));
        switchTheme.setChecked(prefs.getBoolean(PREF_DARK_THEME, true));
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            Close();
        });

        btnChangePassword.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            sendPasswordResetEmail();
        });

        btnPrivacy.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            showPrivacyDialog();
        });

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                prefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        btnTextSize.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            showTextSizeDialog();
        });

        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                handleNotificationsToggle(isChecked);
            }
        });

        btnClearCache.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            clearAppCache();
        });

        btnSupport.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 30);
            contactSupport();
        });

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

    // --- АНИМАЦИИ И СИСТЕМНЫЕ МЕТОДЫ ---

    private void handleRevealAnimation(Bundle savedInstanceState) {
        if (savedInstanceState == null && getIntent().hasExtra("revealX")) {
            mainContentLayout.setVisibility(View.INVISIBLE);
            ViewTreeObserver viewTreeObserver = mainContentLayout.getViewTreeObserver();
            if (viewTreeObserver.isAlive()) {
                viewTreeObserver.addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        mainContentLayout.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        revealActivity(getIntent().getIntExtra("revealX", 0), getIntent().getIntExtra("revealY", 0));
                    }
                });
            }
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

    public void Close() {
        int revealX = getIntent().getIntExtra("revealX", 0);
        int revealY = getIntent().getIntExtra("revealY", 0);
        unRevealActivity(revealX, revealY);
    }

    @Override
    public void onBackPressed() {
        Close();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) {
            return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        }
        return super.dispatchTouchEvent(ev);
    }

    // --- ЛОГИКА ФУНКЦИЙ ---

    private void showPrivacyDialog() {
        String[] privacyOptions = {
                "Закрытый профиль (только для друзей)",
                "Скрыть меня из глобального поиска",
                "Скрыть статус «В сети»"
        };

        boolean[] checkedItems = {
                prefs.getBoolean(PREF_PRIVACY_CLOSED_PROFILE, false),
                prefs.getBoolean(PREF_PRIVACY_HIDE_SEARCH, false),
                prefs.getBoolean(PREF_PRIVACY_HIDE_ONLINE, false)
        };

        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories)
                .setTitle("Конфиденциальность")
                .setMultiChoiceItems(privacyOptions, checkedItems, (dialog, which, isChecked) -> {
                    checkedItems[which] = isChecked;
                })
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    prefs.edit()
                            .putBoolean(PREF_PRIVACY_CLOSED_PROFILE, checkedItems[0])
                            .putBoolean(PREF_PRIVACY_HIDE_SEARCH, checkedItems[1])
                            .putBoolean(PREF_PRIVACY_HIDE_ONLINE, checkedItems[2])
                            .apply();

                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        FirebaseDatabase.getInstance().getReference("users")
                                .child(currentUser.getUid())
                                .child("privacy")
                                .child("hide_online")
                                .setValue(checkedItems[2]);

                        if (checkedItems[2]) {
                            FirebaseDatabase.getInstance().getReference("users")
                                    .child(currentUser.getUid())
                                    .child("status")
                                    .setValue("hidden");
                        } else {
                            FirebaseDatabase.getInstance().getReference("users")
                                    .child(currentUser.getUid())
                                    .child("status")
                                    .setValue("online");
                        }
                    }

                    Toast.makeText(this, "Настройки приватности сохранены", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void showTextSizeDialog() {
        String[] sizes = {"Мелкий", "Обычный", "Крупный"};
        float[] scaleValues = {0.85f, 1.0f, 1.15f};

        float currentScale = prefs.getFloat(PREF_TEXT_SCALE, 1.0f);
        int checkedItem = 1;

        if (currentScale == 0.85f) checkedItem = 0;
        else if (currentScale == 1.15f) checkedItem = 2;

        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories)
                .setTitle("Размер текста")
                .setSingleChoiceItems(sizes, checkedItem, (dialog, which) -> {
                    float selectedScale = scaleValues[which];
                    prefs.edit().putFloat(PREF_TEXT_SCALE, selectedScale).apply();
                    dialog.dismiss();
                    recreate();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

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

    private void contactSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
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
}
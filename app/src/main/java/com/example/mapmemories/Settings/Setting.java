package com.example.mapmemories.Settings;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.animation.AccelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
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

    // Новые свитчи
    private SwitchMaterial switchTheme, switchNotifications, switchMic, switchGallery;

    private SharedPreferences prefs;
    public static final String PREFS_NAME = "AppPrefs";
    private static final String PREF_NOTIFICATIONS = "notifications_enabled";
    private static final String PREF_DARK_THEME = "dark_theme_enabled";
    public static final String PREF_TEXT_SCALE = "text_scale";

    private static final String PREF_PRIVACY_CLOSED_PROFILE = "privacy_closed_profile";
    private static final String PREF_PRIVACY_HIDE_SEARCH = "privacy_hide_search";
    private static final String PREF_PRIVACY_HIDE_ONLINE = "privacy_hide_online";

    private boolean isClosing = false;

    // Лаунчеры для запроса разрешений
    private ActivityResultLauncher<String> requestNotificationLauncher;
    private ActivityResultLauncher<String> requestMicLauncher;
    private ActivityResultLauncher<String> requestGalleryLauncher;

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

        initLaunchers();
        initViews();
        loadSettings();
        setupClickListeners();
        handleRevealAnimation(savedInstanceState);
    }

    private void initLaunchers() {
        requestNotificationLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            prefs.edit().putBoolean(PREF_NOTIFICATIONS, isGranted).apply();
            switchNotifications.setChecked(isGranted);
            if (isGranted) startService(new Intent(this, MessageListenerService.class));
            else stopService(new Intent(this, MessageListenerService.class));
        });

        requestMicLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            switchMic.setChecked(isGranted);
            if (!isGranted) Toast.makeText(this, "Микрофон недоступен", Toast.LENGTH_SHORT).show();
        });

        requestGalleryLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            switchGallery.setChecked(isGranted);
            if (!isGranted) Toast.makeText(this, "Галерея недоступна", Toast.LENGTH_SHORT).show();
        });
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
        switchMic = findViewById(R.id.switchMic);
        switchGallery = findViewById(R.id.switchGallery);
    }

    private void loadSettings() {
        switchTheme.setChecked(prefs.getBoolean(PREF_DARK_THEME, true));
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncPermissions(); // При возврате в настройки перепроверяем реальные разрешения системы
    }

    private void syncPermissions() {
        // Убираем слушатели на секунду, чтобы свитч не вызывал код при программном переключении
        switchNotifications.setOnCheckedChangeListener(null);
        switchMic.setOnCheckedChangeListener(null);
        switchGallery.setOnCheckedChangeListener(null);

        // Проверяем реальные системные допуски
        boolean hasNotification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boolean hasMic = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;

        String galleryPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
        boolean hasGallery = ContextCompat.checkSelfPermission(this, galleryPerm) == PackageManager.PERMISSION_GRANTED;

        switchNotifications.setChecked(hasNotification);
        switchMic.setChecked(hasMic);
        switchGallery.setChecked(hasGallery);
        prefs.edit().putBoolean(PREF_NOTIFICATIONS, hasNotification).apply();

        setupSwitchListeners();
    }

    private void setupSwitchListeners() {
        // Уведомления
        switchNotifications.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            VibratorHelper.vibrate(this, 30);
            if (isChecked) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) showSettingsDialog("Уведомления", switchNotifications);
                    else requestNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                } else {
                    prefs.edit().putBoolean(PREF_NOTIFICATIONS, true).apply();
                    startService(new Intent(this, MessageListenerService.class));
                }
            } else {
                prefs.edit().putBoolean(PREF_NOTIFICATIONS, false).apply();
                stopService(new Intent(this, MessageListenerService.class));
            }
        });

        // Микрофон
        switchMic.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            VibratorHelper.vibrate(this, 30);
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) showSettingsDialog("Микрофон", switchMic);
                    else requestMicLauncher.launch(Manifest.permission.RECORD_AUDIO);
                }
            } else {
                showSettingsDialog("Микрофон", switchMic);
            }
        });

        // Галерея
        switchGallery.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (!buttonView.isPressed()) return;
            VibratorHelper.vibrate(this, 30);
            String galleryPerm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU ? Manifest.permission.READ_MEDIA_IMAGES : Manifest.permission.READ_EXTERNAL_STORAGE;
            if (isChecked) {
                if (ContextCompat.checkSelfPermission(this, galleryPerm) != PackageManager.PERMISSION_GRANTED) {
                    if (!shouldShowRequestPermissionRationale(galleryPerm)) showSettingsDialog("Галерея", switchGallery);
                    else requestGalleryLauncher.launch(galleryPerm);
                }
            } else {
                showSettingsDialog("Галерея", switchGallery);
            }
        });
    }

    private void showSettingsDialog(String type, SwitchMaterial switchToRevert) {
        Dialog dialog = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));

        View activityRootView = getWindow().getDecorView().findViewById(android.R.id.content);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) {
            activityRootView.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(20f, 20f, android.graphics.Shader.TileMode.MIRROR));
        }

        android.widget.FrameLayout rootLayout = new android.widget.FrameLayout(this);
        rootLayout.setBackgroundColor(Color.parseColor("#66000000"));
        rootLayout.setAlpha(0f);

        com.google.android.material.card.MaterialCardView cardView = new com.google.android.material.card.MaterialCardView(this);
        cardView.setCardBackgroundColor(ContextCompat.getColor(this, R.color.chat_me_bg));
        cardView.setRadius(48f);
        cardView.setCardElevation(20f);
        android.widget.FrameLayout.LayoutParams cardParams = new android.widget.FrameLayout.LayoutParams(
                (int) (getResources().getDisplayMetrics().widthPixels * 0.85),
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
        );
        cardParams.gravity = android.view.Gravity.CENTER;
        cardView.setLayoutParams(cardParams);

        android.widget.LinearLayout cardContent = new android.widget.LinearLayout(this);
        cardContent.setOrientation(android.widget.LinearLayout.VERTICAL);
        cardContent.setPadding(64, 64, 64, 48);

        TextView title = new TextView(this);
        title.setText("Требуется разрешение");
        title.setTextSize(20f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        title.setGravity(android.view.Gravity.CENTER);
        cardContent.addView(title);

        TextView desc = new TextView(this);
        desc.setText("Разрешение для «" + type + "» заблокировано. Чтобы использовать эту функцию, включите его в настройках вашего телефона.");
        desc.setTextSize(14f);
        desc.setTextColor(ContextCompat.getColor(this, R.color.text_secondary));
        desc.setGravity(android.view.Gravity.CENTER);
        desc.setPadding(0, 24, 0, 48);
        cardContent.addView(desc);

        android.widget.LinearLayout btnContainer = new android.widget.LinearLayout(this);
        btnContainer.setOrientation(android.widget.LinearLayout.HORIZONTAL);

        TextView btnCancel = new TextView(this);
        btnCancel.setText("Отмена");
        btnCancel.setGravity(android.view.Gravity.CENTER);
        btnCancel.setTextSize(16f);
        btnCancel.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        btnCancel.setPadding(0, 32, 0, 32);
        btnCancel.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView btnSettings = new TextView(this);
        btnSettings.setText("В настройки");
        btnSettings.setGravity(android.view.Gravity.CENTER);
        btnSettings.setTextSize(16f);
        btnSettings.setTypeface(null, android.graphics.Typeface.BOLD);
        btnSettings.setTextColor(Color.parseColor("#E27950")); // Твой акцентный цвет
        btnSettings.setPadding(0, 32, 0, 32);
        btnSettings.setLayoutParams(new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        btnContainer.addView(btnCancel);
        btnContainer.addView(btnSettings);
        cardContent.addView(btnContainer);

        cardView.addView(cardContent);
        rootLayout.addView(cardView);
        dialog.setContentView(rootLayout);

        rootLayout.animate().alpha(1f).setDuration(200).start();
        cardView.setScaleX(0.8f); cardView.setScaleY(0.8f);
        cardView.animate().scaleX(1f).scaleY(1f).setDuration(300).setInterpolator(new android.view.animation.OvershootInterpolator(1.2f)).start();

        Runnable closeDialog = () -> {
            rootLayout.animate().alpha(0f).setDuration(200).withEndAction(() -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) {
                    activityRootView.setRenderEffect(null);
                }
                dialog.dismiss();
                syncPermissions(); // Возвращаем свитч на место
            }).start();
        };

        btnCancel.setOnClickListener(v -> closeDialog.run());
        btnSettings.setOnClickListener(v -> {
            closeDialog.run();
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.fromParts("package", getPackageName(), null));
            startActivity(intent);
        });

        dialog.setOnCancelListener(d -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activityRootView != null) activityRootView.setRenderEffect(null);
            syncPermissions();
        });

        dialog.show();
    }

    private void setupClickListeners() {
        btnBack.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); Close(); });
        btnChangePassword.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); sendPasswordResetEmail(); });
        btnPrivacy.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); showPrivacyDialog(); });

        switchTheme.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (buttonView.isPressed()) {
                VibratorHelper.vibrate(this, 30);
                prefs.edit().putBoolean(PREF_DARK_THEME, isChecked).apply();
                AppCompatDelegate.setDefaultNightMode(isChecked ? AppCompatDelegate.MODE_NIGHT_YES : AppCompatDelegate.MODE_NIGHT_NO);
            }
        });

        btnTextSize.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); showTextSizeDialog(); });
        btnClearCache.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); clearAppCache(); });
        btnSupport.setOnClickListener(v -> { VibratorHelper.vibrate(this, 30); contactSupport(); });

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

        btnDeleteAccount.setOnClickListener(v -> { VibratorHelper.vibrate(this, 100); showDeleteAccountDialog(); });
    }

    // --- АНИМАЦИИ И СИСТЕМНЫЕ МЕТОДЫ ОСТАЛИСЬ КАК БЫЛИ ---
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
        if (mainContentLayout == null) { finish(); overridePendingTransition(0, 0); return; }
        float finalRadius = (float) (Math.max(mainContentLayout.getWidth(), mainContentLayout.getHeight()) * 1.1);
        Animator circularReveal = ViewAnimationUtils.createCircularReveal(mainContentLayout, x, y, finalRadius, 0);
        circularReveal.setDuration(400);
        circularReveal.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) {
                mainContentLayout.setVisibility(View.INVISIBLE);
                finish();
                overridePendingTransition(0, 0);
            }
        });
        circularReveal.start();
    }

    public void Close() {
        if (isClosing) return;
        isClosing = true;
        if (getIntent().hasExtra("revealX") && mainContentLayout != null) {
            unRevealActivity(getIntent().getIntExtra("revealX", 0), getIntent().getIntExtra("revealY", 0));
        } else {
            finish();
            overridePendingTransition(0, 0);
        }
    }

    @Override public void onBackPressed() { Close(); }

    @Override public boolean dispatchTouchEvent(MotionEvent ev) {
        if (swipeBackHelper != null) return swipeBackHelper.dispatchTouchEvent(ev, event -> super.dispatchTouchEvent(event));
        return super.dispatchTouchEvent(ev);
    }

    // --- МЕТОДЫ ДИАЛОГОВ ---
    private void showPrivacyDialog() {
        String[] privacyOptions = {"Закрытый профиль (только для друзей)", "Скрыть меня из глобального поиска", "Скрыть статус «В сети»"};
        boolean[] checkedItems = {prefs.getBoolean(PREF_PRIVACY_CLOSED_PROFILE, false), prefs.getBoolean(PREF_PRIVACY_HIDE_SEARCH, false), prefs.getBoolean(PREF_PRIVACY_HIDE_ONLINE, false)};
        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories).setTitle("Конфиденциальность").setMultiChoiceItems(privacyOptions, checkedItems, (dialog, which, isChecked) -> checkedItems[which] = isChecked)
                .setPositiveButton("Сохранить", (dialog, which) -> {
                    prefs.edit().putBoolean(PREF_PRIVACY_CLOSED_PROFILE, checkedItems[0]).putBoolean(PREF_PRIVACY_HIDE_SEARCH, checkedItems[1]).putBoolean(PREF_PRIVACY_HIDE_ONLINE, checkedItems[2]).apply();
                    FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
                    if (currentUser != null) {
                        FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("privacy").child("hide_online").setValue(checkedItems[2]);
                        FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid()).child("status").setValue(checkedItems[2] ? "hidden" : "online");
                    }
                    Toast.makeText(this, "Настройки сохранены", Toast.LENGTH_SHORT).show();
                }).setNegativeButton("Отмена", null).show();
    }

    private void showTextSizeDialog() {
        String[] sizes = {"Мелкий", "Обычный", "Крупный"};
        float[] scaleValues = {0.85f, 1.0f, 1.15f};
        float currentScale = prefs.getFloat(PREF_TEXT_SCALE, 1.0f);
        int checkedItem = currentScale == 0.85f ? 0 : (currentScale == 1.15f ? 2 : 1);
        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories).setTitle("Размер текста").setSingleChoiceItems(sizes, checkedItem, (dialog, which) -> {
            prefs.edit().putFloat(PREF_TEXT_SCALE, scaleValues[which]).apply();
            dialog.dismiss();
            recreate();
        }).setNegativeButton("Отмена", null).show();
    }

    private void sendPasswordResetEmail() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null && user.getEmail() != null) {
            FirebaseAuth.getInstance().sendPasswordResetEmail(user.getEmail()).addOnCompleteListener(task -> Toast.makeText(this, task.isSuccessful() ? "Письмо отправлено на " + user.getEmail() : "Ошибка при отправке письма", Toast.LENGTH_LONG).show());
        }
    }

    private void contactSupport() {
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"shvi.coffein@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, "MapMemories: Обращение в поддержку");
        try { startActivity(Intent.createChooser(intent, "Написать разработчикам")); } catch (Exception ex) { Toast.makeText(this, "Нет установленных почтовых клиентов", Toast.LENGTH_SHORT).show(); }
    }

    private void showDeleteAccountDialog() {
        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories).setTitle("Удаление аккаунта").setMessage("Это действие необратимо. Вы уверены?").setPositiveButton("Удалить", (dialog, which) -> Toast.makeText(this, "Функция в разработке", Toast.LENGTH_LONG).show()).setNegativeButton("Отмена", null).show();
    }

    private void clearAppCache() {
        try { Toast.makeText(this, deleteDir(getCacheDir()) ? "Кэш очищен" : "Кэш уже пуст", Toast.LENGTH_SHORT).show(); } catch (Exception e) { Toast.makeText(this, "Ошибка", Toast.LENGTH_SHORT).show(); }
    }

    private boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            if (children != null) { for (String child : children) { if (!deleteDir(new File(dir, child))) return false; } }
            return dir.delete();
        } else if (dir != null && dir.isFile()) return dir.delete();
        return false;
    }
}
package com.example.mapmemories.LogRegStart;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.BounceInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.VibratorHelper;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private ImageView logoImageView;
    private ImageView rippleCircle1, rippleCircle2;
    private TextView appNameTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        // Инициализация
        logoImageView = findViewById(R.id.logoImageView);
        rippleCircle1 = findViewById(R.id.rippleCircle1);
        rippleCircle2 = findViewById(R.id.rippleCircle2);
        appNameTextView = findViewById(R.id.appNameTextView);

        // 1. ПОДГОТОВКА: Убираем логотип наверх за пределы экрана
        // translationY(-2000) поднимает его высоко вверх
        logoImageView.setTranslationY(-2500f);

        // Подготавливаем текст (смещаем вниз и делаем прозрачным)
        appNameTextView.setAlpha(0f);
        appNameTextView.setTranslationY(100f);

        // ЗАПУСК АНИМАЦИИ
        startDropAnimation();
    }

    private void startDropAnimation() {
        // --- ЭТАП 1: ПАДЕНИЕ ---
        // Логотип летит вниз. Используем BounceInterpolator, чтобы он "ударился" и подпрыгнул
        logoImageView.animate()
                .translationY(0f)
                .setDuration(1200) // Длительность падения и отскоков
                .setStartDelay(300) // Небольшая пауза перед началом
                .setInterpolator(new BounceInterpolator())
                .start();

        // --- ЭТАП 2: УДАР (IMPACT) ---
        // Мы знаем, что при BounceInterpolator первый "удар" об землю происходит
        // примерно на 1/3 или 1/4 от времени анимации. Подбираем тайминг.
        // Если duration 1200, то удар где-то через 600-700мс после начала (с учетом задержки)

        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            // 1. Сильная вибрация "Удар"
            VibratorHelper.vibrate(this, 70);

            // 2. Расходящиеся круги (Shockwave)
            playShockwaveAnimation();

        }, 850); // <-- Тайминг удара (300 delay + ~550 полёт)


        // --- ЭТАП 3: ПОЯВЛЕНИЕ ТЕКСТА ---
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Легкая вибрация при появлении текста
            VibratorHelper.vibrate(this, 20);

            appNameTextView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(600)
                    .setInterpolator(new OvershootInterpolator()) // Текст чуть "перелетит" и вернется
                    .start();

        }, 1400);

        // --- ЭТАП 4: ПЕРЕХОД ДАЛЬШЕ ---
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            finish();
        }, 2500); // Общее время заставки
    }

    private void playShockwaveAnimation() {
        // Круг 1: Быстрый и резкий
        rippleCircle1.setAlpha(0.7f);
        rippleCircle1.setScaleX(1f);
        rippleCircle1.setScaleY(1f);

        rippleCircle1.animate()
                .scaleX(3.5f)
                .scaleY(3.5f)
                .alpha(0f)
                .setDuration(600)
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();

        // Круг 2: Чуть медленнее, для "эхо" эффекта
        rippleCircle2.setAlpha(0.5f);
        rippleCircle2.setScaleX(1f);
        rippleCircle2.setScaleY(1f);

        rippleCircle2.animate()
                .scaleX(4.5f)
                .scaleY(4.5f)
                .alpha(0f)
                .setDuration(800)
                .setStartDelay(100) // Чуть позже первого
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .start();
    }
}
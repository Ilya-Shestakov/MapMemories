package com.example.mapmemories;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logoImageView = findViewById(R.id.logoImageView);
        ImageView rippleCircle1 = findViewById(R.id.rippleCircle1);
        ImageView rippleCircle2 = findViewById(R.id.rippleCircle2);
        TextView appNameTextView = findViewById(R.id.appNameTextView);

        // Простая вибрация при запуске
        VibratorHelper.vibrate(this, 50);

        // Анимация появления лого
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logoImageView.startAnimation(fadeIn);

        ScaleAnimation ripple1 = new ScaleAnimation(
                0.5f, 1.5f,
                0.5f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        ripple1.setDuration(1000);
        ripple1.setRepeatCount(Animation.INFINITE);
        ripple1.setRepeatMode(Animation.RESTART);

        ScaleAnimation ripple2 = new ScaleAnimation(
                0.5f, 1.5f,
                0.5f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        ripple2.setDuration(1000);
        ripple2.setStartOffset(500);
        ripple2.setRepeatCount(Animation.INFINITE);
        ripple2.setRepeatMode(Animation.RESTART);

        rippleCircle1.startAnimation(ripple1);
        rippleCircle2.startAnimation(ripple2);

        // Постепенно показываем круги с вибрацией
        new Handler().postDelayed(() -> {
            rippleCircle1.animate().alpha(0.3f).setDuration(500).start();

        }, 200);

        new Handler().postDelayed(() -> {
            rippleCircle2.animate().alpha(0.3f).setDuration(500).start();
        }, 400);

        // Показываем текст с вибрацией
        new Handler().postDelayed(() -> {
            appNameTextView.animate().alpha(1f).setDuration(500).start();

        }, 1000);

        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));

            VibratorHelper.vibrate(this, 100, 100);
            VibratorHelper.vibrate(this, 100, 200);

            finish();
        }, 1500);
    }
}
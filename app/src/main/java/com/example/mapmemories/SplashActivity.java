package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
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

        // Анимация появления лого
        Animation fadeIn = AnimationUtils.loadAnimation(this, R.anim.fade_in);
        logoImageView.startAnimation(fadeIn);

        // Эффект ряби 1
        ScaleAnimation ripple1 = new ScaleAnimation(
                0.5f, 1.5f,
                0.5f, 1.5f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        ripple1.setDuration(1000);
        ripple1.setRepeatCount(Animation.INFINITE);
        ripple1.setRepeatMode(Animation.RESTART);

        // Эффект ряби 2 (с задержкой)
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

        // Постепенно показываем круги
        new Handler().postDelayed(() -> {
            rippleCircle1.animate().alpha(0.3f).setDuration(500).start();
        }, 200);

        new Handler().postDelayed(() -> {
            rippleCircle2.animate().alpha(0.3f).setDuration(500).start();
        }, 400);

        // Показываем текст
        new Handler().postDelayed(() -> {
            appNameTextView.animate().alpha(1f).setDuration(500).start();
        }, 1000);

        // Переход через 3 секунды
        new Handler().postDelayed(() -> {
            startActivity(new Intent(SplashActivity.this, OnboardingActivity.class));
            finish();
        }, 1500);
    }
}
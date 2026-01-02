package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

import org.osmdroid.config.Configuration;

public class MainActivity extends AppCompatActivity {

    private ViewPager2 viewPager;
    private TextView onlineIndicator, offlineIndicator, onlineText, offlineText;
    private ImageView logoutButton;
    private ImageButton profileButton;
    private LinearLayout onlineContainer, offlineContainer;
    private FirebaseAuth mAuth;

    private ViewPagerAdapter viewPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration.getInstance().load(this,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

        mAuth = FirebaseAuth.getInstance();

        checkCurrentUser();
        initViews();

        setupViewPager();

        setupClickListeners();
        updateUserInfo();
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        }
    }

    private void initViews() {
        viewPager = findViewById(R.id.viewPager);

        // Индикаторы
        onlineContainer = findViewById(R.id.onlineContainer);
        offlineContainer = findViewById(R.id.offlineContainer);

        onlineIndicator = findViewById(R.id.onlineIndicator);
        offlineIndicator = findViewById(R.id.offlineIndicator);

        onlineText = findViewById(R.id.onlineText);
        offlineText = findViewById(R.id.offlineText);

        // Кнопки
        profileButton = findViewById(R.id.profileButton);
        logoutButton = findViewById(R.id.logoutButton);
    }

    private void setupViewPager() {
        viewPagerAdapter = new ViewPagerAdapter(this);
        viewPager.setAdapter(viewPagerAdapter);

        viewPager.setUserInputEnabled(false);

        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                updateIndicators(position);
            }
        });

        updateIndicators(0);
    }

    private void updateIndicators(int position) {
        if (position == 0) {
            // Активен онлайн режим
            onlineIndicator.setAlpha(1f);
            onlineText.setTextColor(getColor(R.color.text_primary));

            offlineIndicator.setAlpha(0.5f);
            offlineText.setTextColor(getColor(R.color.text_secondary));
        } else {
            // Активен оффлайн режим
            onlineIndicator.setAlpha(0.5f);
            onlineText.setTextColor(getColor(R.color.text_secondary));

            offlineIndicator.setAlpha(1f);
            offlineText.setTextColor(getColor(R.color.text_primary));
        }
    }

    private void setupClickListeners() {
        profileButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                VibratorHelper.vibrate(MainActivity.this, 50);
                Intent intent = new Intent(MainActivity.this, Profile.class);
                startActivity(intent);
                finish();
            }
        });

        logoutButton.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 100);
            showLogoutConfirmation();
        });

        onlineContainer.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            viewPager.setCurrentItem(0, true);
        });

        // Клик по индикатору оффлайн
        offlineContainer.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            viewPager.setCurrentItem(1, true);
        });
    }

    private void showLogoutConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы уверены, что хотите выйти?")
                .setPositiveButton("Выйти", (dialog, which) -> {
                    logoutUser();
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void logoutUser() {
        mAuth.signOut();
        Toast.makeText(this, "Вы вышли из аккаунта", Toast.LENGTH_SHORT).show();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    private void updateUserInfo() {
        FirebaseUser user = mAuth.getCurrentUser();
        if (user != null) {
            // Можно обновить иконку профиля, если есть фото
            // Toast.makeText(this, "Добро пожаловать, " + user.getEmail(), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (viewPager.getCurrentItem() == 0) {
            new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle("Выход")
                    .setMessage("Нажмите ещё раз для выхода")
                    .setPositiveButton("Выйти", (dialog, which) -> {
                        super.onBackPressed();
                    })
                    .setNegativeButton("Отмена", null)
                    .show();
        } else {
            super.onBackPressed();
        }
    }



}
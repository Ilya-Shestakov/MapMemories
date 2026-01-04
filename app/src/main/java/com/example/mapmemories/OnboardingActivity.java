package com.example.mapmemories;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.ScaleAnimation;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;
import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 onboardingViewPager;
    private Button buttonSkip;
    private Button buttonNext;
    private LinearLayout indicatorLayout;
    private OnboardingAdapter onboardingAdapter;
    private Drawable drawable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        android.content.SharedPreferences preferences = getSharedPreferences("onboarding_prefs", MODE_PRIVATE);

        if (preferences.getBoolean("is_onboarding_completed", false)) {
            startMainActivity();
            return;
        }


        setContentView(R.layout.activity_onboarding);

        onboardingViewPager = findViewById(R.id.onboardingViewPager);
        buttonSkip = findViewById(R.id.skipButton);
        buttonNext = findViewById(R.id.nextButton);
        indicatorLayout = findViewById(R.id.indicatorLayout);

        List<OnboardingItem> onboardingItems = new ArrayList<>();

        onboardingItems.add(new OnboardingItem(
                R.drawable.onboarding_bg1,
                getString(R.string.onboarding_title_1),
                getString(R.string.onboarding_desc_1)
        ));

        onboardingItems.add(new OnboardingItem(
                R.drawable.onboarding_bg2,
                getString(R.string.onboarding_title_2),
                getString(R.string.onboarding_desc_2)
        ));

        onboardingItems.add(new OnboardingItem(
                R.drawable.onboarding_bg3,
                getString(R.string.onboarding_title_3),
                getString(R.string.onboarding_desc_3)
        ));

        onboardingAdapter = new OnboardingAdapter(onboardingItems);
        onboardingViewPager.setAdapter(onboardingAdapter);

        setupIndicators();

        new Handler().postDelayed(() -> {
            if (onboardingViewPager.getCurrentItem() == 0) {
                animateFirstSlide();
            }
        }, 1000);

        setCurrentIndicator(0);


        onboardingViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                super.onPageSelected(position);
                setCurrentIndicator(position);
                updateButtons(position);
            }
        });


        buttonSkip.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startMainActivity();
            }
        });


        buttonNext.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (onboardingViewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                    onboardingViewPager.setCurrentItem(onboardingViewPager.getCurrentItem() + 1);
                } else {
                    startMainActivity();
                }
            }
        });


        updateButtons(0);
    }


    private void animateFirstSlide() {
        ImageView backgroundImage = findViewById(R.id.backgroundImage);

        ScaleAnimation pulse = new ScaleAnimation(
                1f, 1.05f, 1f, 1.05f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f
        );
        pulse.setDuration(800);
        pulse.setRepeatCount(Animation.INFINITE);
        pulse.setRepeatMode(Animation.REVERSE);

        backgroundImage.startAnimation(pulse);
    }


    private void setupIndicators() {
        ImageView[] indicators = new ImageView[onboardingAdapter.getItemCount()];
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0); // Отступы между точками

        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(this);
            indicators[i].setImageDrawable(ContextCompat.getDrawable(
                    this,
                    R.drawable.indicator_dot_inactive
            ));
            indicators[i].setLayoutParams(params);
            indicatorLayout.addView(indicators[i]);
        }
    }

    private void setCurrentIndicator(int position) {
        int childCount = indicatorLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) indicatorLayout.getChildAt(i);
            if (i == position) {
                imageView.setImageDrawable(ContextCompat.getDrawable(
                        this,
                        R.drawable.indicator_dot_active
                ));
            } else {
                imageView.setImageDrawable(ContextCompat.getDrawable(
                        this,
                        R.drawable.indicator_dot_inactive
                ));
            }
        }
    }

    private void updateButtons(int position) {
        if (position == onboardingAdapter.getItemCount() - 1) {
            buttonNext.setText(getString(R.string.start));
            buttonSkip.setText("");
            buttonSkip.setVisibility(View.VISIBLE);
        } else {
            buttonNext.setText(getString(R.string.next));
            buttonSkip.setText("Пропустить");
            buttonSkip.setVisibility(View.VISIBLE);
        }
    }

    private void startMainActivity() {

        android.content.SharedPreferences preferences = getSharedPreferences("onboarding_prefs", MODE_PRIVATE);

        android.content.SharedPreferences.Editor editor = preferences.edit();

        editor.putBoolean("is_onboarding_completed", true);

        editor.apply();

        Intent intent = new Intent(OnboardingActivity.this, LoginActivity.class);
        startActivity(intent);
        finish();
    }
}
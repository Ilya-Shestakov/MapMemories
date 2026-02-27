package com.example.mapmemories.LogRegStart;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.viewpager2.widget.ViewPager2;

import com.example.mapmemories.R;

import java.util.ArrayList;
import java.util.List;

public class OnboardingActivity extends AppCompatActivity {

    private ViewPager2 onboardingViewPager;
    private Button buttonSkip, buttonNext;
    private LinearLayout indicatorLayout;
    private OnboardingAdapter onboardingAdapter;
    private SharedPreferences preferences;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        preferences = getSharedPreferences("onboarding_prefs", MODE_PRIVATE);
        if (preferences.getBoolean("is_onboarding_completed", false)) {
            startLoginActivity();
            return;
        }

        setContentView(R.layout.activity_onboarding);

        initViews();
        setupViewPager();
        setupIndicators();
        setupClickListeners();
    }

    private void initViews() {
        onboardingViewPager = findViewById(R.id.onboardingViewPager);
        buttonSkip = findViewById(R.id.skipButton);
        buttonNext = findViewById(R.id.nextButton);
        indicatorLayout = findViewById(R.id.indicatorLayout);
    }

    private void setupViewPager() {
        List<OnboardingItem> items = new ArrayList<>();
        items.add(new OnboardingItem(R.drawable.onboarding_bg1, getString(R.string.onboarding_title_1), getString(R.string.onboarding_desc_1)));
        items.add(new OnboardingItem(R.drawable.onboarding_bg2, getString(R.string.onboarding_title_2), getString(R.string.onboarding_desc_2)));
        items.add(new OnboardingItem(R.drawable.onboarding_bg3, getString(R.string.onboarding_title_3), getString(R.string.onboarding_desc_3)));

        onboardingAdapter = new OnboardingAdapter(items);
        onboardingViewPager.setAdapter(onboardingAdapter);

        onboardingViewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                setCurrentIndicator(position);
                updateButtons(position);
            }
        });
    }

    private void setupIndicators() {
        ImageView[] indicators = new ImageView[onboardingAdapter.getItemCount()];
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(8, 0, 8, 0);

        for (int i = 0; i < indicators.length; i++) {
            indicators[i] = new ImageView(this);
            indicators[i].setImageDrawable(ContextCompat.getDrawable(this, R.drawable.indicator_dot_inactive));
            indicators[i].setLayoutParams(params);
            indicatorLayout.addView(indicators[i]);
        }

        setCurrentIndicator(0);
        updateButtons(0);
    }

    private void setCurrentIndicator(int position) {
        int childCount = indicatorLayout.getChildCount();
        for (int i = 0; i < childCount; i++) {
            ImageView imageView = (ImageView) indicatorLayout.getChildAt(i);
            int drawableId = (i == position) ? R.drawable.indicator_dot_active : R.drawable.indicator_dot_inactive;
            imageView.setImageDrawable(ContextCompat.getDrawable(this, drawableId));
        }
    }

    private void updateButtons(int position) {
        if (position == onboardingAdapter.getItemCount() - 1) {
            buttonNext.setText(getString(R.string.start));
            buttonSkip.setVisibility(View.INVISIBLE);
        } else {
            buttonNext.setText(getString(R.string.next));
            buttonSkip.setVisibility(View.VISIBLE);
        }
    }

    private void setupClickListeners() {
        buttonSkip.setOnClickListener(v -> startLoginActivity());

        buttonNext.setOnClickListener(v -> {
            if (onboardingViewPager.getCurrentItem() + 1 < onboardingAdapter.getItemCount()) {
                onboardingViewPager.setCurrentItem(onboardingViewPager.getCurrentItem() + 1);
            } else {
                startLoginActivity();
            }
        });
    }

    private void startLoginActivity() {
        preferences.edit().putBoolean("is_onboarding_completed", true).apply();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }
}
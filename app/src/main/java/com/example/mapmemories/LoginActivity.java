package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText emailEditText, passwordEditText;
    private TextInputLayout emailInputLayout, passwordInputLayout;
    private Button loginButton;
    private TextView registerButton, forgotPasswordTextView;
    private ProgressBar progressBar;
    private RelativeLayout overlay;

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAuth = FirebaseAuth.getInstance();
        checkCurrentUser();

        initViews();
        setupClickListeners();
    }

    private void checkCurrentUser() {
        try {
            FirebaseUser currentUser = mAuth.getCurrentUser();
            if (currentUser != null) {
                // Пользователь уже авторизован, переходим на главный экран
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
        } catch (Exception e) {
            // Игнорируем ошибку, просто продолжаем
            e.printStackTrace();
        }
    }

    private void initViews() {
        // Находим TextInputLayout
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);

        // Получаем TextInputEditText из TextInputLayout
        if (emailInputLayout != null) {
            emailEditText = (TextInputEditText) emailInputLayout.getEditText();
        }

        if (passwordInputLayout != null) {
            passwordEditText = (TextInputEditText) passwordInputLayout.getEditText();
        }

        // Остальные View
        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        progressBar = findViewById(R.id.progressBar);
        overlay = findViewById(R.id.overlay);

        // Скрываем ProgressBar и overlay
        showLoading(false);
    }

    private void setupClickListeners() {
        // Кнопка входа
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptLogin();
            }
        });

        // Кнопка регистрации
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // Забыли пароль
        forgotPasswordTextView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showForgotPasswordDialog();
            }
        });
    }

    private void attemptLogin() {
        // Сброс ошибок
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        // Получаем значения
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();

        boolean hasError = false;

        // Валидация email
        if (TextUtils.isEmpty(email)) {
            emailInputLayout.setError("Введите email");
            hasError = true;
        } else if (!isValidEmail(email)) {
            emailInputLayout.setError("Некорректный email");
            hasError = true;
        }

        // Валидация пароля
        if (TextUtils.isEmpty(password)) {
            passwordInputLayout.setError("Введите пароль");
            hasError = true;
        } else if (password.length() < 6) {
            passwordInputLayout.setError("Пароль должен быть минимум 6 символов");
            hasError = true;
        }

        if (!hasError) {
            showLoading(true);

            try {
                mAuth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(this, task -> {
                            showLoading(false);

                            if (task.isSuccessful()) {
                                FirebaseUser user = mAuth.getCurrentUser();
                                if (user != null) {
                                    Log.d("LOGIN", "User logged in: " + user.getEmail());
                                    Toast.makeText(LoginActivity.this,
                                            "Добро пожаловать!",
                                            Toast.LENGTH_SHORT).show();
                                    startActivity(new Intent(LoginActivity.this, MainActivity.class));
                                    finish();
                                }
                            } else {
                                // Детальная информация об ошибке
                                Exception exception = task.getException();
                                Log.e("LOGIN", "Login failed", exception);

                                String errorMessage = "Ошибка входа";
                                if (exception != null) {
                                    errorMessage = exception.getMessage();
                                    Log.e("LOGIN", "Error details: " + exception.getClass().getName());
                                    Log.e("LOGIN", "Error message: " + exception.getMessage());
                                }

                                Toast.makeText(LoginActivity.this, errorMessage,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
            } catch (Exception e) {
                showLoading(false);
                Log.e("LOGIN", "Exception in login", e);
                Toast.makeText(this, "Ошибка: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }
    }

    private void showForgotPasswordDialog() {
        String email = emailEditText.getText().toString().trim();

        if (TextUtils.isEmpty(email) || !isValidEmail(email)) {
            Toast.makeText(this, "Введите email для восстановления",
                    Toast.LENGTH_SHORT).show();
            return;
        }

        showLoading(true);

        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);

                    if (task.isSuccessful()) {
                        Toast.makeText(this,
                                "Инструкция по восстановлению отправлена на " + email,
                                Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this,
                                "Ошибка отправки email. Проверьте адрес",
                                Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            loginButton.setEnabled(false);
            registerButton.setEnabled(false);
            forgotPasswordTextView.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
            loginButton.setEnabled(true);
            registerButton.setEnabled(true);
            forgotPasswordTextView.setEnabled(true);
        }
    }
}
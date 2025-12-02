package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText usernameEditText, emailEditText, passwordEditText, confirmPasswordEditText;
    private TextInputLayout usernameInputLayout, emailInputLayout, passwordInputLayout, confirmPasswordInputLayout;
    private Button registerButton, loginButton;
    private ImageButton backButton;
    private ProgressBar progressBar;
    private RelativeLayout overlay;

    // Firebase
    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);

        // Инициализация Firebase
        mAuth = FirebaseAuth.getInstance();

        // Инициализация views
        initViews();

        // Обработчики кликов
        setupClickListeners();
    }

    private void initViews() {
        // TextInputEditText
        usernameEditText = findViewById(R.id.usernameEditText);
        emailEditText = findViewById(R.id.emailEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        confirmPasswordEditText = findViewById(R.id.confirmPasswordEditText);

        // TextInputLayout
        usernameInputLayout = findViewById(R.id.usernameInputLayout);
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);
        confirmPasswordInputLayout = findViewById(R.id.confirmPasswordInputLayout);

        // Кнопки
        registerButton = findViewById(R.id.registerButton);
        loginButton = findViewById(R.id.loginButton);
        backButton = findViewById(R.id.backButton);

        // Прогресс и overlay
        progressBar = findViewById(R.id.progressBar);
        overlay = findViewById(R.id.overlay);

        // Скрываем ProgressBar и overlay
        showLoading(false);
    }

    private void setupClickListeners() {
        // Кнопка регистрации
        registerButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                attemptRegistration();
            }
        });

        // Кнопка входа (уже есть аккаунт)
        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(RegisterActivity.this, LoginActivity.class));
                finish();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            }
        });

        // Кнопка назад
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });
    }

    private void attemptRegistration() {
        // Сброс ошибок
        usernameInputLayout.setError(null);
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);
        confirmPasswordInputLayout.setError(null);

        // Получаем значения
        String username = usernameEditText.getText().toString().trim();
        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString();
        String confirmPassword = confirmPasswordEditText.getText().toString();

        boolean hasError = false;

        // Валидация имени пользователя
        if (TextUtils.isEmpty(username)) {
            usernameInputLayout.setError("Введите имя пользователя");
            hasError = true;
        } else if (username.length() < 3) {
            usernameInputLayout.setError("Минимум 3 символа");
            hasError = true;
        } else if (username.length() > 20) {
            usernameInputLayout.setError("Максимум 20 символов");
            hasError = true;
        }

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
            passwordInputLayout.setError("Минимум 6 символов");
            hasError = true;
        }

        // Валидация подтверждения пароля
        if (TextUtils.isEmpty(confirmPassword)) {
            confirmPasswordInputLayout.setError("Подтвердите пароль");
            hasError = true;
        } else if (!password.equals(confirmPassword)) {
            confirmPasswordInputLayout.setError("Пароли не совпадают");
            hasError = true;
        }

        if (!hasError) {
            // Показываем ProgressBar
            showLoading(true);

            // Регистрация в Firebase
            mAuth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) {
                            // Регистрация успешна
                            FirebaseUser firebaseUser = mAuth.getCurrentUser();

                            if (firebaseUser != null) {
                                // Обновляем displayName в Firebase Auth
                                com.google.firebase.auth.UserProfileChangeRequest profileUpdates =
                                        new com.google.firebase.auth.UserProfileChangeRequest.Builder()
                                                .setDisplayName(username)
                                                .build();

                                firebaseUser.updateProfile(profileUpdates)
                                        .addOnCompleteListener(profileTask -> {
                                            if (profileTask.isSuccessful()) {
                                                Toast.makeText(RegisterActivity.this,
                                                        "Регистрация успешна!",
                                                        Toast.LENGTH_SHORT).show();

                                                // Переход на главный экран
                                                startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                                                finish();
                                            } else {
                                                showLoading(false);
                                                Toast.makeText(RegisterActivity.this,
                                                        "Ошибка сохранения имени",
                                                        Toast.LENGTH_SHORT).show();
                                            }
                                        });
                            }

                        } else {
                            // Ошибка регистрации
                            showLoading(false);
                            String errorMessage = "Ошибка регистрации";

                            if (task.getException() != null) {
                                String exceptionMessage = task.getException().getMessage();
                                if (exceptionMessage != null) {
                                    if (exceptionMessage.contains("email address is already in use")) {
                                        errorMessage = "Этот email уже используется";
                                    } else if (exceptionMessage.contains("network error")) {
                                        errorMessage = "Проверьте подключение к интернету";
                                    } else if (exceptionMessage.contains("invalid email")) {
                                        errorMessage = "Некорректный email";
                                    } else if (exceptionMessage.contains("password is weak")) {
                                        errorMessage = "Пароль слишком слабый";
                                    }
                                }
                            }

                            Toast.makeText(this, errorMessage,
                                    Toast.LENGTH_LONG).show();
                        }
                    });
        } else {
            showLoading(false);
        }
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showLoading(boolean isLoading) {
        if (isLoading) {
            progressBar.setVisibility(View.VISIBLE);
            overlay.setVisibility(View.VISIBLE);
            registerButton.setEnabled(false);
            loginButton.setEnabled(false);
            backButton.setEnabled(false);
        } else {
            progressBar.setVisibility(View.GONE);
            overlay.setVisibility(View.GONE);
            registerButton.setEnabled(true);
            loginButton.setEnabled(true);
            backButton.setEnabled(true);
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
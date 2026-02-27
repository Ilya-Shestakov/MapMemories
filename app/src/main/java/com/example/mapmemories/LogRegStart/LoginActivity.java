package com.example.mapmemories.LogRegStart;

import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.example.mapmemories.Lenta.MainActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.VibratorHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser != null) {
            startActivity(new Intent(LoginActivity.this, MainActivity.class));
            finish();
        }
    }

    private void initViews() {
        emailInputLayout = findViewById(R.id.emailInputLayout);
        passwordInputLayout = findViewById(R.id.passwordInputLayout);

        if (emailInputLayout != null) emailEditText = (TextInputEditText) emailInputLayout.getEditText();
        if (passwordInputLayout != null) passwordEditText = (TextInputEditText) passwordInputLayout.getEditText();

        loginButton = findViewById(R.id.loginButton);
        registerButton = findViewById(R.id.registerButton);
        forgotPasswordTextView = findViewById(R.id.forgotPasswordTextView);
        progressBar = findViewById(R.id.progressBar);
        overlay = findViewById(R.id.overlay);

        showLoading(false);
    }

    private void setupClickListeners() {
        loginButton.setOnClickListener(v -> attemptLogin());

        registerButton.setOnClickListener(v -> {
            startActivity(new Intent(LoginActivity.this, RegisterActivity.class));
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        });

        forgotPasswordTextView.setOnClickListener(v -> showForgotPasswordDialog());
    }

    private void attemptLogin() {
        emailInputLayout.setError(null);
        passwordInputLayout.setError(null);

        String email = emailEditText.getText().toString().trim();
        String password = passwordEditText.getText().toString().trim();
        boolean hasError = false;

        if (TextUtils.isEmpty(email) || !isValidEmail(email)) {
            emailInputLayout.setError("Введите корректный email");
            VibratorHelper.vibrate(this, 50);
            hasError = true;
        }

        if (TextUtils.isEmpty(password) || password.length() < 6) {
            passwordInputLayout.setError("Пароль должен быть минимум 6 символов");
            VibratorHelper.vibrate(this, 50);
            hasError = true;
        }

        if (!hasError) {
            showLoading(true);
            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        showLoading(false);
                        if (task.isSuccessful()) {
                            VibratorHelper.vibrate(this, 30);
                            startActivity(new Intent(LoginActivity.this, MainActivity.class));
                            finish();
                        } else {
                            VibratorHelper.vibrate(this, 100);
                            Toast.makeText(LoginActivity.this, "Ошибка входа. Проверьте данные.", Toast.LENGTH_LONG).show();
                        }
                    });
        }
    }

    private void showForgotPasswordDialog() {
        VibratorHelper.vibrate(this, 30);

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        input.setHint("Введите ваш Email");

        // Берем email из поля ввода, если юзер уже начал его писать
        String currentEmail = emailEditText.getText().toString().trim();
        if (!TextUtils.isEmpty(currentEmail)) {
            input.setText(currentEmail);
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setPadding(50, 40, 50, 10);
        layout.addView(input);

        new MaterialAlertDialogBuilder(this, R.style.Theme_MapMemories) // Убедись что тема подходит, или убери второй аргумент
                .setTitle("Восстановление пароля")
                .setMessage("Мы отправим ссылку для сброса пароля на вашу почту.")
                .setView(layout)
                .setPositiveButton("Отправить", (dialog, which) -> {
                    String email = input.getText().toString().trim();
                    if (TextUtils.isEmpty(email) || !isValidEmail(email)) {
                        Toast.makeText(this, "Введите корректный email", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    sendResetEmail(email);
                })
                .setNegativeButton("Отмена", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void sendResetEmail(String email) {
        showLoading(true);
        mAuth.sendPasswordResetEmail(email)
                .addOnCompleteListener(task -> {
                    showLoading(false);
                    if (task.isSuccessful()) {
                        Toast.makeText(this, "Инструкция отправлена на " + email, Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(this, "Ошибка отправки. Проверьте адрес.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private boolean isValidEmail(String email) {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches();
    }

    private void showLoading(boolean isLoading) {
        int visibility = isLoading ? View.VISIBLE : View.GONE;
        progressBar.setVisibility(visibility);
        overlay.setVisibility(visibility);
        loginButton.setEnabled(!isLoading);
        registerButton.setEnabled(!isLoading);
        forgotPasswordTextView.setEnabled(!isLoading);
    }
}
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
import androidx.cardview.widget.CardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class CreatePostActivity extends AppCompatActivity {

    private ImageButton btnClose;
    private CardView btnAddMedia;
    private ImageView previewImage;
    private TextInputEditText inputTitle, inputDescription;
    private LinearLayout btnSelectLocation;
    private TextView textLocation;
    private MaterialButton btnSavePost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_post);

        initViews();
        setupListeners();
    }

    private void initViews() {
        btnClose = findViewById(R.id.btnClose);
        btnAddMedia = findViewById(R.id.btnAddMedia);
        previewImage = findViewById(R.id.previewImage);
        inputTitle = findViewById(R.id.inputTitle);
        inputDescription = findViewById(R.id.inputDescription);
        btnSelectLocation = findViewById(R.id.btnSelectLocation);
        textLocation = findViewById(R.id.textLocation);
        btnSavePost = findViewById(R.id.btnSavePost);
    }

    private void setupListeners() {
        // Закрыть активити
        btnClose.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        // Добавить фото
        btnAddMedia.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            Toast.makeText(this, "Открыть галерею", Toast.LENGTH_SHORT).show();
            // TODO: Реализовать выбор фото
        });

        // Выбрать локацию
        btnSelectLocation.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            Toast.makeText(this, "Открыть карту для выбора места", Toast.LENGTH_SHORT).show();
            // TODO: Открыть карту и вернуть координаты
        });

        // Сохранить
        btnSavePost.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            String title = inputTitle.getText().toString();
            String desc = inputDescription.getText().toString();

            if (title.isEmpty()) {
                inputTitle.setError("Введите название");
                return;
            }

            Toast.makeText(this, "Пост сохранен: " + title, Toast.LENGTH_SHORT).show();
            // TODO: Сохранить в Firebase
            finish();
        });
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
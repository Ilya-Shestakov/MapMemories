package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView; // Важно: используем ImageView
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import org.osmdroid.config.Configuration;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView memoriesRecyclerView;
    private FloatingActionButton fabAdd, fabMap;
    private ImageView logoutButton;
    private ImageView profileButton; // Теперь это ImageView

    // Firebase
    private FirebaseAuth mAuth;
    private DatabaseReference userRef;

    // Для двойного нажатия "Назад"
    private long backPressedTime;
    private Toast backToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Configuration.getInstance().load(this,
                androidx.preference.PreferenceManager.getDefaultSharedPreferences(this));

        mAuth = FirebaseAuth.getInstance();

        checkCurrentUser();
        initViews();
        setupClickListeners();
        setupRecyclerView();

        // Настройка выхода по двойному клику
        setupDoubleBackExit();

        // Загрузка фото
        loadUserAvatar();
    }

    private void checkCurrentUser() {
        FirebaseUser currentUser = mAuth.getCurrentUser();
        if (currentUser == null) {
            startActivity(new Intent(this, LoginActivity.class));
            finish();
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("users").child(currentUser.getUid());
        }
    }

    private void initViews() {
        memoriesRecyclerView = findViewById(R.id.memoriesRecyclerView);
        fabAdd = findViewById(R.id.fabAdd);
        fabMap = findViewById(R.id.fabMap);
        // Тут теперь ImageView, но findViewById найдет его по ID без проблем
        profileButton = findViewById(R.id.profileButton);
        logoutButton = findViewById(R.id.logoutButton);
    }

    // --- ЛОГИКА ВЫХОДА ПО ДВОЙНОМУ НАЖАТИЮ ---
    private void setupDoubleBackExit() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (backPressedTime + 2000 > System.currentTimeMillis()) {
                    // Если нажали второй раз быстрее чем за 2 секунды
                    if (backToast != null) backToast.cancel();
                    finish(); // Закрываем активити (выходим из приложения)
                } else {
                    // Первое нажатие
                    backToast = Toast.makeText(getBaseContext(), "Нажмите еще раз, чтобы выйти", Toast.LENGTH_SHORT);
                    backToast.show();
                }
                backPressedTime = System.currentTimeMillis();
            }
        });
    }

    // --- ЗАГРУЗКА АВАТАРКИ ---
    private void loadUserAvatar() {
        if (userRef == null) return;

        userRef.child("profileImageUrl").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String profileImageUrl = snapshot.getValue(String.class);

                if (!TextUtils.isEmpty(profileImageUrl)) {
                    // Теперь код очень простой, так как XML исправлен
                    Glide.with(MainActivity.this)
                            .load(profileImageUrl)
                            .placeholder(R.drawable.ic_profile_placeholder) // Показываем пока грузится
                            .circleCrop() // Обрезаем в круг
                            .into(profileButton);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Игнорируем ошибки
            }
        });
    }

    private void setupRecyclerView() {
        memoriesRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        List<String> dummyList = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            dummyList.add("Item " + i);
        }
        SimpleAdapter adapter = new SimpleAdapter(dummyList);
        memoriesRecyclerView.setAdapter(adapter);
    }

    private void setupClickListeners() {
        profileButton.setOnClickListener(view -> {
            VibratorHelper.vibrate(MainActivity.this, 50);
            startActivity(new Intent(this, Profile.class));
            finish();
        });

        logoutButton.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 100);
            showLogoutConfirmation();
        });

        fabAdd.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            Toast.makeText(this, "Открыть создание поста", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, CreatePostActivity.class));
            finish();
        });

        fabMap.setOnClickListener(v -> {
            VibratorHelper.vibrate(this, 50);
            Toast.makeText(this, "Открыть карту", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, Setting.class));
            finish();
        });
    }

    private void showLogoutConfirmation() {
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Выход")
                .setMessage("Вы уверены, что хотите выйти?")
                .setPositiveButton("Выйти", (dialog, which) -> logoutUser())
                .setNegativeButton("Отмена", null)
                .show();
    }

    private void logoutUser() {
        mAuth.signOut();
        startActivity(new Intent(this, LoginActivity.class));
        finish();
    }

    // Простой адаптер для примера
    class SimpleAdapter extends RecyclerView.Adapter<SimpleAdapter.ViewHolder> {
        private List<String> data;
        public SimpleAdapter(List<String> data) { this.data = data; }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_memory_card, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) { }

        @Override
        public int getItemCount() { return data.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView title;
            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                title = itemView.findViewById(R.id.cardTitle);
            }
        }
    }
}
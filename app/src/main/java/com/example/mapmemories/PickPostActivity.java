package com.example.mapmemories;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PickPostActivity extends AppCompatActivity {

    private RecyclerView recyclerView;
    private MemoriesAdapter adapter; // Используем твой существующий адаптер!
    private List<Post> myPosts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile); // ХАК: Используем макет профиля или создай простой с RecyclerView
        // Лучше создай простой layout: activity_pick_post.xml с одним RecyclerView

        // ВРЕМЕННОЕ РЕШЕНИЕ: Создаем RecyclerView программно, чтобы не плодить XML,
        // но лучше создай activity_pick_post.xml
        recyclerView = new RecyclerView(this);
        recyclerView.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.MATCH_PARENT));
        recyclerView.setBackgroundColor(getResources().getColor(R.color.primary));
        setContentView(recyclerView);

        recyclerView.setLayoutManager(new GridLayoutManager(this, 2)); // Сетка по 2

        myPosts = new ArrayList<>();
        // Используем твой MemoriesAdapter, но переопределяем клик
        adapter = new MemoriesAdapter(this, myPosts, post -> {
            // ПРИ КЛИКЕ ВОЗВРАЩАЕМ РЕЗУЛЬТАТ
            Intent resultIntent = new Intent();
            resultIntent.putExtra("selectedPostId", post.getId());
            setResult(RESULT_OK, resultIntent);
            finish();
        });
        recyclerView.setAdapter(adapter);

        loadMyPosts();
    }

    private void loadMyPosts() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference ref = FirebaseDatabase.getInstance().getReference("posts");

        ref.orderByChild("userId").equalTo(uid).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                myPosts.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Post post = ds.getValue(Post.class);
                    if (post != null) {
                        myPosts.add(post);
                    }
                }
                Collections.reverse(myPosts); // Новые сверху
                adapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
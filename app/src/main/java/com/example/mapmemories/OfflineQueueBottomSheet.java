package com.example.mapmemories;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mapmemories.database.AppDatabase;
import com.example.mapmemories.database.OfflinePost;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.Executors;

public class OfflineQueueBottomSheet extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private TextView tvEmptyState;
    private OfflineMemoriesAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_offline_queue, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Делаем фон прозрачным, чтобы углы были скругленные (если у тебя в стиле это не задано)
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        recyclerView = view.findViewById(R.id.recyclerOffline);
        tvEmptyState = view.findViewById(R.id.tvEmptyState);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new OfflineMemoriesAdapter(getContext(), new ArrayList<>(), this::deleteOfflinePost);
        recyclerView.setAdapter(adapter);

        // Подписка на обновления базы
        AppDatabase.getDatabase(getContext()).offlinePostDao().getAllPostsLive().observe(getViewLifecycleOwner(), posts -> {
            if (posts != null && !posts.isEmpty()) {
                adapter.updateList(posts);
                tvEmptyState.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
            } else {
                adapter.updateList(new ArrayList<>());
                tvEmptyState.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
                // Можно раскомментировать, если хочешь, чтобы окно закрывалось само, когда пусто
                // dismiss();
            }
        });
    }

    private void deleteOfflinePost(OfflinePost post) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                if (post.mediaUriString != null) {
                    File file = new File(post.mediaUriString);
                    if (file.exists()) file.delete();
                }
            } catch (Exception e) { e.printStackTrace(); }
            AppDatabase.getDatabase(getContext()).offlinePostDao().delete(post);
        });
        VibratorHelper.vibrate(getContext(), 20);
        Toast.makeText(getContext(), "Удалено", Toast.LENGTH_SHORT).show();
    }
}
package com.example.mapmemories.Profile;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.mapmemories.R;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;

public class FriendsBottomSheetDialogFragment extends BottomSheetDialogFragment {

    private RecyclerView recyclerView;
    private TextView titleText;
    private UsersAdapter adapter;
    private List<User> userList;

    private String mode; // "friends", "followers", "following"
    private String currentUserId;

    public static FriendsBottomSheetDialogFragment newInstance(String mode, String userId) {
        FriendsBottomSheetDialogFragment fragment = new FriendsBottomSheetDialogFragment();
        Bundle args = new Bundle();
        args.putString("mode", mode);
        args.putString("userId", userId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_friends_bottom_sheet, container, false);

        if (getArguments() != null) {
            mode = getArguments().getString("mode");
            currentUserId = getArguments().getString("userId");
        }

        titleText = view.findViewById(R.id.sheetTitle);
        recyclerView = view.findViewById(R.id.friendsRecyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        userList = new ArrayList<>();
        adapter = new UsersAdapter(getContext(), userList, user -> {
            // Переход в профиль
            Intent intent = new Intent(getContext(), UserProfileActivity.class);
            intent.putExtra("targetUserId", user.getId());
            startActivity(intent);
            dismiss(); // Закрываем шторку
        });
        recyclerView.setAdapter(adapter);

        loadUsers();

        return view;
    }

    private void loadUsers() {
        DatabaseReference rootRef = FirebaseDatabase.getInstance().getReference();
        DatabaseReference listRef;

        switch (mode) {
            case "followers":
                titleText.setText("Подписчики");
                // requests_incoming - это те, кто хочет добавиться ко мне (мои подписчики)
                listRef = rootRef.child("users").child(currentUserId).child("requests_incoming");
                break;
            case "following":
                titleText.setText("Подписки");
                // requests_sent - это те, к кому я постучался (мои подписки)
                listRef = rootRef.child("users").child(currentUserId).child("requests_sent");
                break;
            default: // "friends"
                titleText.setText("Друзья");
                listRef = rootRef.child("users").child(currentUserId).child("friends");
                break;
        }

        listRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                userList.clear();
                if (!snapshot.exists()) {
                    adapter.notifyDataSetChanged();
                    return;
                }

                for (DataSnapshot ds : snapshot.getChildren()) {
                    String userId = ds.getKey();
                    loadUserDetails(userId);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadUserDetails(String userId) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(userId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    User user = snapshot.getValue(User.class);
                    if (user != null) {
                        user.setId(snapshot.getKey());
                        userList.add(user);
                        adapter.notifyDataSetChanged();
                    }
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
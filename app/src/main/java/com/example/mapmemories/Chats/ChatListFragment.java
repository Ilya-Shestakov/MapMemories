package com.example.mapmemories.Chats;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.example.mapmemories.Lenta.MainActivity;
import com.example.mapmemories.Profile.User;
import com.example.mapmemories.Profile.UsersAdapter;
import com.example.mapmemories.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;
import com.google.firebase.database.ValueEventListener;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import android.graphics.Color;
import java.util.Collections;

import java.util.ArrayList;
import java.util.List;

public class ChatListFragment extends Fragment {

    private RecyclerView chatsRecyclerView, globalRecyclerView;
    private EditText searchInput;
    private TextView emptyChatsText, localChatsTitle;
    private LinearLayout globalSearchContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView chatScrollView;

    private ChatListAdapter chatListAdapter;
    private UsersAdapter globalUsersAdapter;

    private List<User> localUsersList = new ArrayList<>();
    private List<User> filteredLocalList = new ArrayList<>();
    private List<User> globalUserList = new ArrayList<>();

    private List<String> pinnedUserIds = new ArrayList<>(); // Храним ID закрепленных
    private DatabaseReference myPinnedRef; // Ссылка на закрепленные чаты в БД

    private DatabaseReference chatsRef, usersRef;
    private String currentUserId;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return view;

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");

        // Ссылка на закрепленные чаты текущего юзера
        myPinnedRef = usersRef.child(currentUserId).child("pinnedChats");

        initViews(view);
        setupRecyclerViews();
        setupSearch();
        setupSwipeRefresh();

        loadPinnedChats(); // Сначала грузим закрепленные, потом сами чаты

        return view;
    }

    private void initViews(View v) {
        chatsRecyclerView = v.findViewById(R.id.chatsRecyclerView);
        globalRecyclerView = v.findViewById(R.id.globalRecyclerView);
        searchInput = v.findViewById(R.id.searchInput);
        emptyChatsText = v.findViewById(R.id.emptyChatsText);
        localChatsTitle = v.findViewById(R.id.localChatsTitle);
        globalSearchContainer = v.findViewById(R.id.globalSearchContainer);
        swipeRefreshLayout = v.findViewById(R.id.swipeRefreshLayout);
        chatScrollView = v.findViewById(R.id.chatScrollView);
    }

    private void setupRecyclerViews() {
        // 1. Мои чаты (используем твой ChatListAdapter)
        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatListAdapter = new ChatListAdapter(getContext(), filteredLocalList, new ChatListAdapter.OnChatClickListener() {
            @Override
            public void onChatClick(User user) {
                openChat(user.getId());
            }

            @Override
            public void onChatLongClick(User user) {
                showChatOptionsDialog(user); // ВЫЗЫВАЕМ МЕНЮ
            }
        });
        chatsRecyclerView.setAdapter(chatListAdapter);

        // 2. Глобальный поиск (используем твой UsersAdapter с 3-мя аргументами)
        globalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        globalUsersAdapter = new UsersAdapter(getContext(), globalUserList, user -> {
            openChat(user.getId());
        });
        globalRecyclerView.setAdapter(globalUsersAdapter);

        // Анимация Dock при скролле
        chatScrollView.setOnScrollChangeListener((NestedScrollView.OnScrollChangeListener) (v, scrollX, scrollY, oldScrollX, oldScrollY) -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).toggleBottomDock(scrollY <= oldScrollY);
            }
        });
    }

    private void openChat(String targetUserId) {
        Intent intent = new Intent(getActivity(), ChatActivity.class);
        intent.putExtra("targetUserId", targetUserId);
        startActivity(intent);
    }

    private void loadPinnedChats() {
        myPinnedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pinnedUserIds.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    pinnedUserIds.add(ds.getKey());
                }
                loadLocalChats(); // После получения закрепленных, грузим чаты
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void loadLocalChats() {
        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                localUsersList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String chatId = ds.getKey();
                    if (chatId != null && chatId.contains(currentUserId)) {
                        // Вычисляем ID собеседника из ключа чата (например "ID1_ID2")
                        String otherUserId = chatId.replace(currentUserId, "").replace("_", "");
                        fetchUserInfo(otherUserId);
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchUserInfo(String userId) {
        usersRef.child(userId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                User user = snapshot.getValue(User.class);
                if (user != null) {
                    user.setId(snapshot.getKey()); // Устанавливаем ID вручную, так как он @Exclude

                    // Проверка на дубликаты в списке
                    boolean exists = false;
                    for (User u : localUsersList) {
                        if (u.getId() != null && u.getId().equals(user.getId())) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        localUsersList.add(user);
                        updateLocalFilter(searchInput.getText().toString());
                    }
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void showChatOptionsDialog(User user) {
        BottomSheetDialog bottomSheetDialog = new BottomSheetDialog(getContext());
        View view = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_chat_options, null);
        bottomSheetDialog.setContentView(view);
        ((View) view.getParent()).setBackgroundColor(Color.TRANSPARENT);

        TextView tvPinText = view.findViewById(R.id.tvPinText);
        LinearLayout btnPinChat = view.findViewById(R.id.btnPinChat);
        LinearLayout btnDeleteChat = view.findViewById(R.id.btnDeleteChat);

        boolean isPinned = pinnedUserIds.contains(user.getId());
        tvPinText.setText(isPinned ? "Открепить" : "Закрепить");

        // ЛОГИКА ЗАКРЕПЛЕНИЯ
        btnPinChat.setOnClickListener(v -> {
            if (isPinned) {
                myPinnedRef.child(user.getId()).removeValue(); // Открепляем
            } else {
                myPinnedRef.child(user.getId()).setValue(true); // Закрепляем
            }
            bottomSheetDialog.dismiss();
        });

        // ЛОГИКА УДАЛЕНИЯ
        btnDeleteChat.setOnClickListener(v -> {
            // Генерируем ID чата так же, как в ChatActivity
            String chatId = currentUserId.compareTo(user.getId()) < 0 ?
                    currentUserId + "_" + user.getId() : user.getId() + "_" + currentUserId;

            // Удаляем чат из БД
            chatsRef.child(chatId).removeValue();

            // Удаляем из локального списка, чтобы UI обновился мгновенно
            localUsersList.remove(user);
            updateLocalFilter(searchInput.getText().toString());

            bottomSheetDialog.dismiss();
        });

        bottomSheetDialog.show();
    }

    private void setupSearch() {
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim().toLowerCase();
                updateLocalFilter(query);

                if (TextUtils.isEmpty(query)) {
                    globalSearchContainer.setVisibility(View.GONE);
                    localChatsTitle.setVisibility(View.GONE);
                } else {
                    localChatsTitle.setVisibility(View.VISIBLE);
                    performGlobalSearch(query);
                }
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void updateLocalFilter(String query) {
        filteredLocalList.clear();
        if (TextUtils.isEmpty(query)) {
            filteredLocalList.addAll(localUsersList);
        } else {
            for (User user : localUsersList) {
                if (user.getUsername() != null && user.getUsername().toLowerCase().contains(query)) {
                    filteredLocalList.add(user);
                }
            }
        }

        // СОРТИРОВКА: Закрепленные сверху
        Collections.sort(filteredLocalList, (u1, u2) -> {
            boolean p1 = pinnedUserIds.contains(u1.getId());
            boolean p2 = pinnedUserIds.contains(u2.getId());
            if (p1 && !p2) return -1; // u1 выше
            if (!p1 && p2) return 1;  // u2 выше
            return 0; // Оставляем как есть
        });

        chatListAdapter.setUsers(filteredLocalList, pinnedUserIds); // Передаем оба списка
        emptyChatsText.setVisibility(filteredLocalList.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void performGlobalSearch(String searchText) {
        Query query = usersRef.orderByChild("username")
                .startAt(searchText)
                .endAt(searchText + "\uf8ff")
                .limitToFirst(15);

        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                globalUserList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    if (user != null) {
                        user.setId(ds.getKey()); // Устанавливаем ID вручную
                        if (!user.getId().equals(currentUserId)) {
                            globalUserList.add(user);
                        }
                    }
                }
                globalSearchContainer.setVisibility(globalUserList.isEmpty() ? View.GONE : View.VISIBLE);
                globalUsersAdapter.notifyDataSetChanged();
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void setupSwipeRefresh() {
        swipeRefreshLayout.setColorSchemeResources(R.color.accent);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            loadLocalChats();
            swipeRefreshLayout.setRefreshing(false);
        });
    }
}
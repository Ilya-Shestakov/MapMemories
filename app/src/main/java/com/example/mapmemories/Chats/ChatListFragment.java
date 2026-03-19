package com.example.mapmemories.Chats;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatListFragment extends Fragment {

    private RecyclerView chatsRecyclerView, globalRecyclerView;
    private EditText searchInput;
    private TextView emptyChatsText, localChatsTitle;
    private LinearLayout globalSearchContainer;
    private SwipeRefreshLayout swipeRefreshLayout;
    private NestedScrollView chatScrollView;

    private ChatListAdapter chatListAdapter;
    private UsersAdapter globalUsersAdapter;

    private List<ChatListItem> allChatListItems = new ArrayList<>();
    private List<ChatListItem> filteredChatListItems = new ArrayList<>();
    private List<User> globalUserList = new ArrayList<>();

    // Карта для мгновенного обновления закрепов
    private Map<String, Long> pinnedChatsMap = new HashMap<>();

    private DatabaseReference chatsRef, usersRef, myPinnedRef;
    private String currentUserId;

    private boolean isDragging = false;
    private boolean pendingUpdate = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_chat_list, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) return view;

        currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        usersRef = FirebaseDatabase.getInstance().getReference("users");
        myPinnedRef = usersRef.child(currentUserId).child("pinnedChats");

        initViews(view);
        setupRecyclerViews();
        setupDragAndDrop();
        setupSearch();
        setupSwipeRefresh();

        loadLocalChatsData();

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
        chatsRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        chatListAdapter = new ChatListAdapter(getContext(), filteredChatListItems, currentUserId, new ChatListAdapter.OnChatInteractionListener() {
            @Override
            public void onChatClick(ChatListItem item) {
                Intent intent = new Intent(getActivity(), ChatActivity.class);
                intent.putExtra("targetUserId", item.user.getId());
                startActivity(intent);
            }

            @Override
            public void onChatLongClick(ChatListItem item, View anchorView) {
                showContextMenu(item, anchorView);
            }
        });
        chatsRecyclerView.setAdapter(chatListAdapter);

        globalRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        globalUsersAdapter = new UsersAdapter(getContext(), globalUserList, user -> {
            Intent intent = new Intent(getActivity(), ChatActivity.class);
            intent.putExtra("targetUserId", user.getId());
            startActivity(intent);
        });
        globalRecyclerView.setAdapter(globalUsersAdapter);
    }

    // --- МЕНЮ ТЕЛЕГРАМ С ПЛАВНЫМ ПЕРЕМЕЩЕНИЕМ ---
    private void showContextMenu(ChatListItem item, View anchorView) {
        View popupView = LayoutInflater.from(getContext()).inflate(R.layout.popup_chat_options, null);

        // Ставим focusable=false, чтобы не прерывать перетаскивание списка при долгом нажатии
        PopupWindow popupWindow = new PopupWindow(
                popupView,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                false
        );

        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setElevation(10f);
        popupWindow.setOutsideTouchable(true);

        TextView btnPin = popupView.findViewById(R.id.btnPopupPin);
        TextView btnDelete = popupView.findViewById(R.id.btnPopupDelete);

        btnPin.setText(item.isPinned ? "Открепить" : "Закрепить");

        btnPin.setOnClickListener(v -> {
            popupWindow.dismiss();
            if (item.isPinned) {
                myPinnedRef.child(item.user.getId()).removeValue();
            } else {
                myPinnedRef.child(item.user.getId()).setValue(-System.currentTimeMillis());
            }
        });

        btnDelete.setOnClickListener(v -> {
            popupWindow.dismiss();
            chatsRef.child(item.chatId).removeValue();
        });

        int[] location = new int[2];
        anchorView.getLocationOnScreen(location);

        int xOffset = 120;

        popupWindow.showAtLocation(anchorView, Gravity.NO_GRAVITY, location[0] + xOffset, location[1] + (anchorView.getHeight() / 2));

        // Логика следования за элементом (когда скроллим или перетаскиваем элемент)
        ViewTreeObserver.OnPreDrawListener preDrawListener = new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                if (popupWindow.isShowing()) {
                    int[] newLoc = new int[2];
                    anchorView.getLocationOnScreen(newLoc);
                    popupWindow.update(newLoc[0] + xOffset, newLoc[1] + (anchorView.getHeight() / 2), -1, -1);
                }
                return true;
            }
        };

        anchorView.getViewTreeObserver().addOnPreDrawListener(preDrawListener);

        popupWindow.setOnDismissListener(() -> {
            anchorView.getViewTreeObserver().removeOnPreDrawListener(preDrawListener);
        });
    }

    // --- ПЛАВНЫЙ DRAG AND DROP ---
    private void setupDragAndDrop() {
        ItemTouchHelper.SimpleCallback touchHelperCallback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {

            // Отключаем возможность перетаскивания для НЕзакрепленных чатов
            @Override
            public int getMovementFlags(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                int position = viewHolder.getAdapterPosition();
                List<ChatListItem> currentList = chatListAdapter.getItems();
                if (position >= 0 && position < currentList.size()) {
                    if (!currentList.get(position).isPinned) {
                        return makeMovementFlags(0, 0); // Перетаскивание запрещено
                    }
                }
                return makeMovementFlags(ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0); // Разрешено
            }

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                int fromPos = viewHolder.getAdapterPosition();
                int toPos = target.getAdapterPosition();

                List<ChatListItem> currentList = chatListAdapter.getItems();
                if (currentList.get(fromPos).isPinned && currentList.get(toPos).isPinned) {
                    chatListAdapter.swapItems(fromPos, toPos);
                    return true;
                }
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {}

            @Override
            public void onSelectedChanged(@Nullable RecyclerView.ViewHolder viewHolder, int actionState) {
                super.onSelectedChanged(viewHolder, actionState);
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                    isDragging = true;
                    if (viewHolder != null) {
                        viewHolder.itemView.setScaleX(1.02f);
                        viewHolder.itemView.setScaleY(1.02f);
                    }
                }
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                viewHolder.itemView.setScaleX(1f);
                viewHolder.itemView.setScaleY(1f);

                savePinnedOrderToFirebase();

                isDragging = false;
                if (pendingUpdate) {
                    pendingUpdate = false;
                    updateLocalFilter(searchInput.getText().toString());
                }
            }
        };

        new ItemTouchHelper(touchHelperCallback).attachToRecyclerView(chatsRecyclerView);
    }

    private void savePinnedOrderToFirebase() {
        List<ChatListItem> list = chatListAdapter.getItems();
        long orderIndex = 0;
        for (ChatListItem item : list) {
            if (item.isPinned) {
                myPinnedRef.child(item.user.getId()).setValue(orderIndex++);
            }
        }
    }

    // --- ЗАГРУЗКА ДАННЫХ ---
    private void loadLocalChatsData() {
        myPinnedRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                pinnedChatsMap.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    Object val = ds.getValue();
                    long order = (val instanceof Number) ? ((Number)val).longValue() : 0L;
                    pinnedChatsMap.put(ds.getKey(), order);
                }

                for (ChatListItem item : allChatListItems) {
                    if (pinnedChatsMap.containsKey(item.user.getId())) {
                        item.isPinned = true;
                        item.pinnedOrder = pinnedChatsMap.get(item.user.getId());
                    } else {
                        item.isPinned = false;
                        item.pinnedOrder = 0L;
                    }
                }
                if (!isDragging) updateLocalFilter(searchInput.getText().toString());
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });

        chatsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                allChatListItems.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    String chatId = ds.getKey();
                    if (chatId != null && chatId.contains(currentUserId)) {
                        String otherUserId = chatId.replace(currentUserId, "").replace("_", "");
                        fetchChatDetails(chatId, otherUserId, ds.child("messages"));
                    }
                }
                if (allChatListItems.isEmpty() && !isDragging) {
                    updateLocalFilter(searchInput.getText().toString());
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void fetchChatDetails(String chatId, String otherUserId, DataSnapshot messagesSnapshot) {
        ChatMessage lastMsg = null;
        int unreadCount = 0;

        for (DataSnapshot msgSnap : messagesSnapshot.getChildren()) {
            ChatMessage msg = msgSnap.getValue(ChatMessage.class);
            if (msg != null && (msg.getDeletedBy() == null || !msg.getDeletedBy().equals(currentUserId))) {
                lastMsg = msg;
                if (msg.getReceiverId().equals(currentUserId) && !msg.isRead()) {
                    unreadCount++;
                }
            }
        }

        final ChatMessage finalLastMsg = lastMsg;
        final int finalUnreadCount = unreadCount;

        usersRef.child(otherUserId).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot userSnap) {
                User user = userSnap.getValue(User.class);
                if (user != null) {
                    user.setId(otherUserId);
                    ChatListItem item = new ChatListItem(chatId, user);
                    item.lastMessage = finalLastMsg;
                    item.unreadCount = finalUnreadCount;

                    if (pinnedChatsMap.containsKey(otherUserId)) {
                        item.isPinned = true;
                        item.pinnedOrder = pinnedChatsMap.get(otherUserId);
                    } else {
                        item.isPinned = false;
                        item.pinnedOrder = 0L;
                    }

                    addAndUpdateList(item);
                }
            }
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private synchronized void addAndUpdateList(ChatListItem newItem) {
        allChatListItems.removeIf(item -> item.chatId.equals(newItem.chatId));
        allChatListItems.add(newItem);
        if (isDragging) pendingUpdate = true;
        else updateLocalFilter(searchInput.getText().toString());
    }

    // --- ПОИСК И СОРТИРОВКА ---
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
        filteredChatListItems.clear();
        if (TextUtils.isEmpty(query)) {
            filteredChatListItems.addAll(allChatListItems);
        } else {
            for (ChatListItem item : allChatListItems) {
                if (item.user.getUsername() != null && item.user.getUsername().toLowerCase().contains(query)) {
                    filteredChatListItems.add(item);
                }
            }
        }

        Collections.sort(filteredChatListItems, (a, b) -> {
            if (a.isPinned && !b.isPinned) return -1;
            if (!a.isPinned && b.isPinned) return 1;

            if (a.isPinned && b.isPinned) {
                return Long.compare(a.pinnedOrder, b.pinnedOrder);
            }

            long timeA = a.lastMessage != null ? a.lastMessage.getTimestamp() : 0;
            long timeB = b.lastMessage != null ? b.lastMessage.getTimestamp() : 0;
            return Long.compare(timeB, timeA);
        });

        chatListAdapter.setChats(filteredChatListItems);
        emptyChatsText.setVisibility(filteredChatListItems.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void performGlobalSearch(String searchText) {
        Query query = usersRef.orderByChild("username").startAt(searchText).endAt(searchText + "\uf8ff").limitToFirst(15);
        query.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                globalUserList.clear();
                for (DataSnapshot ds : snapshot.getChildren()) {
                    User user = ds.getValue(User.class);
                    if (user != null) {
                        user.setId(ds.getKey());
                        if (!user.getId().equals(currentUserId)) globalUserList.add(user);
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
            loadLocalChatsData();
            swipeRefreshLayout.setRefreshing(false);
        });
    }
}
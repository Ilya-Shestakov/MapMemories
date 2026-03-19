package com.example.mapmemories.Chats;

import com.example.mapmemories.Profile.User;

public class ChatListItem {
    public String chatId;
    public User user;
    public ChatMessage lastMessage;
    public int unreadCount;
    public boolean isPinned;
    public long pinnedOrder; // Для сохранения порядка закрепов

    public ChatListItem(String chatId, User user) {
        this.chatId = chatId;
        this.user = user;
        this.unreadCount = 0;
        this.isPinned = false;
        this.pinnedOrder = 0;
    }
}
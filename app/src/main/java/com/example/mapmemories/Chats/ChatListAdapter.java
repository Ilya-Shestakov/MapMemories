package com.example.mapmemories.Chats;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.mapmemories.R;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    private Context context;
    private List<ChatListItem> chatList;
    private String currentUserId;
    private OnChatInteractionListener listener;

    public interface OnChatInteractionListener {
        void onChatClick(ChatListItem item);
        void onChatLongClick(ChatListItem item, View anchorView); // Передаем View для позиционирования меню
    }

    public ChatListAdapter(Context context, List<ChatListItem> chatList, String currentUserId, OnChatInteractionListener listener) {
        this.context = context;
        this.chatList = chatList;
        this.currentUserId = currentUserId;
        this.listener = listener;
    }

    public void setChats(List<ChatListItem> newChats) {
        this.chatList = newChats;
        notifyDataSetChanged();
    }

    public void swapItems(int fromPosition, int toPosition) {
        Collections.swap(chatList, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
    }

    public List<ChatListItem> getItems() {
        return chatList;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        ChatListItem item = chatList.get(position);

        holder.username.setText(item.user.getUsername() != null ? item.user.getUsername() : "Пользователь");
        holder.ivPinnedIcon.setVisibility(item.isPinned ? View.VISIBLE : View.GONE);

        if (item.user.getProfileImageUrl() != null && !item.user.getProfileImageUrl().isEmpty()) {
            Glide.with(context).load(item.user.getProfileImageUrl()).circleCrop().placeholder(R.drawable.ic_profile_placeholder).into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        FirebaseDatabase.getInstance().getReference("users").child(item.user.getId())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Object statusObj = snapshot.child("status").getValue();
                            boolean isHidden = snapshot.child("privacy/hide_online").exists() &&
                                    Boolean.TRUE.equals(snapshot.child("privacy/hide_online").getValue(Boolean.class));
                            String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                            holder.onlineIndicator.setVisibility("в сети".equals(statusText) ? View.VISIBLE : View.GONE);
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        if (item.lastMessage != null) {
            String previewText = "";
            if ("image".equals(item.lastMessage.getType())) {
                previewText = item.lastMessage.getText() != null && !item.lastMessage.getText().isEmpty() ? "📷 " + item.lastMessage.getText() : "📷 Фотография";
            } else if ("post".equals(item.lastMessage.getType())) {
                previewText = "🗺️ Воспоминание";
            } else {
                previewText = item.lastMessage.getText();
            }
            holder.previewText.setText(previewText);

            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm", Locale.getDefault());
            holder.timeText.setText(sdf.format(item.lastMessage.getTimestamp()));

            if (item.lastMessage.getSenderId().equals(currentUserId)) {
                holder.readStatus.setVisibility(View.VISIBLE);
                holder.unreadBadge.setVisibility(View.GONE);
                holder.previewText.setTypeface(null, Typeface.NORMAL);
                holder.previewText.setTextColor(context.getResources().getColor(R.color.text_secondary));

                if (item.lastMessage.isRead()) {
                    holder.readStatus.setImageResource(R.drawable.ic_check_double);
                } else {
                    holder.readStatus.setImageResource(R.drawable.ic_check_single);
                }
            } else {
                holder.readStatus.setVisibility(View.GONE);
                if (item.unreadCount > 0) {
                    holder.unreadBadge.setVisibility(View.VISIBLE);
                    holder.unreadBadge.setText(String.valueOf(item.unreadCount));
                    holder.previewText.setTypeface(null, Typeface.BOLD);
                    holder.previewText.setTextColor(context.getResources().getColor(R.color.text_primary));
                } else {
                    holder.unreadBadge.setVisibility(View.GONE);
                    holder.previewText.setTypeface(null, Typeface.NORMAL);
                    holder.previewText.setTextColor(context.getResources().getColor(R.color.text_secondary));
                }
            }
        } else {
            holder.previewText.setText("Нет сообщений");
            holder.timeText.setText("");
            holder.readStatus.setVisibility(View.GONE);
            holder.unreadBadge.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> listener.onChatClick(item));

        holder.itemView.setOnLongClickListener(v -> {
            listener.onChatLongClick(item, v); // Передаем View, на которое нажали
            return true;
        });
    }

    @Override
    public int getItemCount() { return chatList.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ConstraintLayout rootLayout;
        ImageView avatar, ivPinnedIcon, readStatus;
        TextView username, previewText, timeText, unreadBadge;
        View onlineIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            rootLayout = itemView.findViewById(R.id.chatItemRoot);
            avatar = itemView.findViewById(R.id.chatAvatar);
            username = itemView.findViewById(R.id.chatUsername);
            previewText = itemView.findViewById(R.id.chatPreviewText);
            timeText = itemView.findViewById(R.id.timeText);
            unreadBadge = itemView.findViewById(R.id.unreadBadge);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            ivPinnedIcon = itemView.findViewById(R.id.ivPinnedIcon);
            readStatus = itemView.findViewById(R.id.ivReadStatus);
        }
    }
}
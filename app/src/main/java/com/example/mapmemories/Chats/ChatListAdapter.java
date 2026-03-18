package com.example.mapmemories.Chats;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.bumptech.glide.Glide;
import com.example.mapmemories.R;
import com.example.mapmemories.Profile.User;
import com.example.mapmemories.systemHelpers.TimeFormatter;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.util.ArrayList;
import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    private Context context;
    private List<User> users;
    private List<String> pinnedUserIds = new ArrayList<>(); // Список ID закрепленных
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(User user);
        void onChatLongClick(User user); // НОВЫЙ МЕТОД
    }

    public ChatListAdapter(Context context, List<User> users, OnChatClickListener listener) {
        this.context = context;
        this.users = users;
        this.listener = listener;
    }

    public void setUsers(List<User> newUsers, List<String> pinnedIds) {
        this.users = newUsers;
        this.pinnedUserIds = pinnedIds;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_chat_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        User user = users.get(position);
        holder.username.setText(user.getUsername());

        // Показываем скрепку, если юзер в списке закрепленных
        if (pinnedUserIds.contains(user.getId())) {
            holder.ivPinnedIcon.setVisibility(View.VISIBLE);
        } else {
            holder.ivPinnedIcon.setVisibility(View.GONE);
        }

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(context).load(user.getProfileImageUrl()).circleCrop().into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        FirebaseDatabase.getInstance().getReference("users").child(user.getId())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Object statusObj = snapshot.child("status").getValue();
                            boolean isHidden = snapshot.child("privacy/hide_online").exists() &&
                                    Boolean.TRUE.equals(snapshot.child("privacy/hide_online").getValue(Boolean.class));

                            String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                            holder.statusText.setText(statusText);

                            if ("в сети".equals(statusText)) {
                                holder.onlineIndicator.setVisibility(View.VISIBLE);
                            } else {
                                holder.onlineIndicator.setVisibility(View.GONE);
                            }
                        }
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });

        // Обычный клик
        holder.itemView.setOnClickListener(v -> listener.onChatClick(user));

        // ДОЛГИЙ КЛИК
        holder.itemView.setOnLongClickListener(v -> {
            listener.onChatLongClick(user);
            return true;
        });
    }

    @Override
    public int getItemCount() { return users.size(); }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar, ivPinnedIcon;
        TextView username, statusText;
        View onlineIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.chatAvatar);
            username = itemView.findViewById(R.id.chatUsername);
            statusText = itemView.findViewById(R.id.chatStatusText);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
            ivPinnedIcon = itemView.findViewById(R.id.ivPinnedIcon); // Находим скрепку
        }
    }
}
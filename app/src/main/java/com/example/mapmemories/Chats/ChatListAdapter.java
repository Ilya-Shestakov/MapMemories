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

import java.util.List;

public class ChatListAdapter extends RecyclerView.Adapter<ChatListAdapter.ViewHolder> {

    private Context context;
    private List<User> users;
    private OnChatClickListener listener;

    public interface OnChatClickListener {
        void onChatClick(User user);
    }

    public ChatListAdapter(Context context, List<User> users, OnChatClickListener listener) {
        this.context = context;
        this.users = users;
        this.listener = listener;
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

        if (user.getProfileImageUrl() != null && !user.getProfileImageUrl().isEmpty()) {
            Glide.with(context).load(user.getProfileImageUrl()).circleCrop().into(holder.avatar);
        } else {
            holder.avatar.setImageResource(R.drawable.ic_profile_placeholder);
        }

        // Слушаем статус пользователя в реальном времени
        FirebaseDatabase.getInstance().getReference("users").child(user.getId())
                .addValueEventListener(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        if (snapshot.exists()) {
                            Object statusObj = snapshot.child("status").getValue();
                            boolean isHidden = false;
                            if (snapshot.child("privacy").child("hide_online").exists()) {
                                isHidden = snapshot.child("privacy").child("hide_online").getValue(Boolean.class);
                            }

                            String statusText = TimeFormatter.formatStatus(statusObj, isHidden);
                            holder.statusText.setText(statusText);

                            if (statusText.equals("в сети")) {
                                holder.onlineIndicator.setVisibility(View.VISIBLE);
                                holder.statusText.setTextColor(context.getResources().getColor(R.color.online_indicator));
                            } else {
                                holder.onlineIndicator.setVisibility(View.GONE);
                                holder.statusText.setTextColor(context.getResources().getColor(R.color.text_secondary));
                            }
                        }
                    }

                    @Override
                    public void onCancelled(@NonNull DatabaseError error) {}
                });

        holder.itemView.setOnClickListener(v -> listener.onChatClick(user));
    }

    @Override
    public int getItemCount() {
        return users.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView avatar;
        TextView username;
        TextView statusText;
        View onlineIndicator;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.chatAvatar);
            username = itemView.findViewById(R.id.chatUsername);
            statusText = itemView.findViewById(R.id.chatStatusText);
            onlineIndicator = itemView.findViewById(R.id.onlineIndicator);
        }
    }
}
package com.example.mapmemories;

import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

public class PostUtils {

    /**
     * Логика переключения лайка (Like/Unlike)
     */
    public static void toggleLike(Post post) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        if (currentUserId == null) return;

        DatabaseReference postLikesRef = FirebaseDatabase.getInstance().getReference("posts")
                .child(post.getId())
                .child("likes");

        postLikesRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.hasChild(currentUserId)) {
                    // Уже лайкнуто -> Убираем лайк
                    postLikesRef.child(currentUserId).removeValue();
                    updateUserTotalLikes(post.getUserId(), -1); // Уменьшаем карму автора
                } else {
                    // Не лайкнуто -> Ставим лайк
                    postLikesRef.child(currentUserId).setValue(true);
                    updateUserTotalLikes(post.getUserId(), 1); // Увеличиваем карму автора
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    /**
     * Транзакция для безопасного обновления счетчика лайков в профиле юзера
     */
    private static void updateUserTotalLikes(String authorId, int increment) {
        DatabaseReference userLikesRef = FirebaseDatabase.getInstance().getReference("users")
                .child(authorId)
                .child("likesCount");

        userLikesRef.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData currentData) {
                Long value = currentData.getValue(Long.class);
                if (value == null) {
                    value = 0L;
                }
                long newValue = value + increment;
                if (newValue < 0) newValue = 0; // Не уходим в минус

                currentData.setValue(newValue);
                return Transaction.success(currentData);
            }

            @Override
            public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    }

    /**
     * Привязка UI к базе данных (слушаем изменения в реальном времени)
     */

    public static void bindLikeButton(String postId, ImageView likeIcon, TextView likeCountText) {
        String currentUserId = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid()
                : "";

        DatabaseReference likesRef = FirebaseDatabase.getInstance().getReference("posts")
                .child(postId)
                .child("likes");

        likesRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                long count = snapshot.getChildrenCount();
                if (likeCountText != null) {
                    likeCountText.setText(String.valueOf(count));
                }

                if (snapshot.hasChild(currentUserId)) {
                    // Лайкнуто нами
                    likeIcon.setImageResource(R.drawable.ic_favorite_red);
                    likeIcon.setColorFilter(null); // Убираем тинт, чтобы сердце было красным
                } else {
                    // Не лайкнуто
                    likeIcon.setImageResource(R.drawable.ic_favorite_border);
                    // Устанавливаем цвет контура (серый или accent, в зависимости от темы)
                    likeIcon.setColorFilter(likeIcon.getContext().getResources().getColor(R.color.accent));
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
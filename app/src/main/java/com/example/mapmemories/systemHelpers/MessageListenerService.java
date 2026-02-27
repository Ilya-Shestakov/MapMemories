package com.example.mapmemories.systemHelpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Chats.ChatActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Profile.UserProfileActivity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MessageListenerService extends Service {

    private DatabaseReference notificationsRef;
    private ChildEventListener listener;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        if (currentUser == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        String myUserId = currentUser.getUid();
        notificationsRef = FirebaseDatabase.getInstance().getReference("notifications").child(myUserId);

        listener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                if (snapshot.exists()) {
                    String senderId = snapshot.child("senderId").getValue(String.class);
                    String text = snapshot.child("text").getValue(String.class);
                    String type = snapshot.child("type").getValue(String.class);

                    // Если тип не указан (старые сообщения), считаем что это чат
                    if (type == null) type = "chat";

                    if (type.equals("chat")) {
                        // Для чата проверяем, не открыт ли он сейчас
                        if (ChatActivity.currentChatUserId == null || !ChatActivity.currentChatUserId.equals(senderId)) {
                            fetchUserDataAndShowNotification(senderId, text, type);
                        }
                    } else if (type.equals("friend_request")) {
                        // Заявку в друзья показываем всегда
                        fetchUserDataAndShowNotification(senderId, text, type);
                    }

                    // Удаляем маячок
                    snapshot.getRef().removeValue();
                }
            }

            @Override
            public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override
            public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override
            public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        };

        notificationsRef.addChildEventListener(listener);

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (notificationsRef != null && listener != null) {
            notificationsRef.removeEventListener(listener);
        }
    }

    // Передаем type дальше
    private void fetchUserDataAndShowNotification(String senderId, String text, String type) {
        DatabaseReference userRef = FirebaseDatabase.getInstance().getReference("users").child(senderId);
        userRef.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                String username = snapshot.child("username").getValue(String.class);
                String avatarUrl = snapshot.child("profileImageUrl").getValue(String.class);

                if (username == null || username.isEmpty()) {
                    username = "Пользователь";
                }

                buildAndShowNotification(senderId, username, text, avatarUrl, type);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void buildAndShowNotification(String senderId, String username, String text, String avatarUrl, String type) {
        Intent intent;

        // ВЫБИРАЕМ, КУДА ПЕРЕЙДЕТ ПОЛЬЗОВАТЕЛЬ ПО КЛИКУ
        if (type.equals("friend_request")) {
            intent = new Intent(this, UserProfileActivity.class); // Открываем профиль
        } else {
            intent = new Intent(this, ChatActivity.class); // Открываем чат
        }

        intent.putExtra("targetUserId", senderId);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        PendingIntent pendingIntent = PendingIntent.getActivity(this, (int) System.currentTimeMillis(), intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        String channelId = "app_notifications_channel";
        NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    channelId,
                    "Уведомления приложения",
                    NotificationManager.IMPORTANCE_HIGH
            );
            notificationManager.createNotificationChannel(channel);
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, channelId)
                .setSmallIcon(R.drawable.ic_launcher_background) // Твоя иконка
                .setColor(ContextCompat.getColor(this, R.color.accent))
                .setContentTitle(username)
                .setContentText(text)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL);

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            new Thread(() -> {
                try {
                    Bitmap bitmap = Glide.with(getApplicationContext())
                            .asBitmap()
                            .load(avatarUrl)
                            .circleCrop()
                            .submit()
                            .get();
                    builder.setLargeIcon(bitmap);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                notificationManager.notify((int) System.currentTimeMillis(), builder.build());
            }).start();
        } else {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }
}
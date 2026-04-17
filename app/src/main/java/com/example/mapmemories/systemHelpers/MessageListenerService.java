package com.example.mapmemories.systemHelpers;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.Person;
import androidx.core.graphics.drawable.IconCompat;

import com.bumptech.glide.Glide;
import com.example.mapmemories.Chats.ChatActivity;
import com.example.mapmemories.Chats.ChatMessage;
import com.example.mapmemories.LogRegStart.SplashActivity;
import com.example.mapmemories.R;
import com.example.mapmemories.Settings.Setting;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.Executors;

public class MessageListenerService extends Service {

    private static final String CHANNEL_ID = "mapmemories_chat_channel";
    private FirebaseAuth mAuth;
    private DatabaseReference chatsRef;
    private String currentUserId;

    // Время запуска сервиса (чтобы не присылать уведомления за старые сообщения при перезапуске)
    private long serviceStartTime;

    // Храним ID сообщений, о которых уже уведомили, чтобы не спамить
    private HashSet<String> notifiedMessages = new HashSet<>();
    // Храним слушатели, чтобы очищать их при остановке
    private HashMap<String, ChildEventListener> chatListeners = new HashMap<>();

    @Override
    public void onCreate() {
        super.onCreate();
        mAuth = FirebaseAuth.getInstance();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        SharedPreferences prefs = getSharedPreferences(Setting.PREFS_NAME, Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean("notifications_enabled", true);

        if (!notificationsEnabled || mAuth.getCurrentUser() == null) {
            stopSelf();
            return START_NOT_STICKY;
        }

        currentUserId = mAuth.getCurrentUser().getUid();
        chatsRef = FirebaseDatabase.getInstance().getReference("chats");
        serviceStartTime = System.currentTimeMillis();

        listenToMyChats();

        // START_STICKY говорит системе: "Если убьешь меня ради памяти, перезапусти, как только сможешь"
        return START_STICKY;
    }

    private void listenToMyChats() {
        // Слушаем ВСЕ чаты, но выбираем только те, где есть наш ID
        chatsRef.addChildEventListener(new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                String chatId = snapshot.getKey();
                if (chatId != null && chatId.contains(currentUserId)) {
                    attachMessageListener(chatId);
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    private void attachMessageListener(String chatId) {
        if (chatListeners.containsKey(chatId)) return; // Уже слушаем

        DatabaseReference messagesRef = chatsRef.child(chatId).child("messages");

        ChildEventListener messageListener = new ChildEventListener() {
            @Override
            public void onChildAdded(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {
                ChatMessage message = snapshot.getValue(ChatMessage.class);

                if (message != null
                        && message.getReceiverId() != null
                        && message.getReceiverId().equals(currentUserId)
                        && !message.isRead()) {

                    // Проверяем, что сообщение пришло ПОСЛЕ запуска сервиса и мы его еще не показывали
                    if (message.getTimestamp() > serviceStartTime && !notifiedMessages.contains(message.getMessageId())) {
                        notifiedMessages.add(message.getMessageId());
                        showNotification(message, chatId);
                    }
                }
            }
            @Override public void onChildChanged(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onChildRemoved(@NonNull DataSnapshot snapshot) {}
            @Override public void onChildMoved(@NonNull DataSnapshot snapshot, @Nullable String previousChildName) {}
            @Override public void onCancelled(@NonNull DatabaseError error) {}
        };

        messagesRef.addChildEventListener(messageListener);
        chatListeners.put(chatId, messageListener);
    }

    private void showNotification(ChatMessage message, String chatId) {
        String senderId = message.getSenderId();

        if (senderId.equals(ChatActivity.currentChatUserId)) {
            return;
        }

        // Запрашиваем данные отправителя (Имя и Аватар)
        FirebaseDatabase.getInstance().getReference("users").child(senderId)
                .addListenerForSingleValueEvent(new ValueEventListener() {
                    @Override
                    public void onDataChange(@NonNull DataSnapshot snapshot) {
                        String senderName = snapshot.child("username").getValue(String.class);
                        String senderAvatarUrl = snapshot.child("profileImageUrl").getValue(String.class);

                        if (senderName == null) senderName = "Пользователь";

                        buildAndDisplayNotification(message, senderId, senderName, senderAvatarUrl);
                    }
                    @Override public void onCancelled(@NonNull DatabaseError error) {}
                });
    }

    private void buildAndDisplayNotification(ChatMessage message, String senderId, String senderName, String avatarUrl) {
        // Так как скачивание картинки требует времени, делаем это в фоновом потоке
        Executors.newSingleThreadExecutor().execute(() -> {
            Bitmap avatarBitmap = null;
            try {
                if (avatarUrl != null && !avatarUrl.isEmpty()) {
                    // Скачиваем аватарку через Glide синхронно
                    avatarBitmap = Glide.with(getApplicationContext())
                            .asBitmap()
                            .load(avatarUrl)
                            .circleCrop()
                            .submit(128, 128)
                            .get();
                }
            } catch (Exception e) {
                e.printStackTrace(); // Если не скачалось, будет null, обработаем ниже
            }

            // Формируем текст (если это картинка, пишем "Фотография")
            String messageText = message.getText();
            if ("image".equals(message.getType())) {
                messageText = "📷 Фотография";
            } else if ("post".equals(message.getType())) {
                messageText = "🗺️ Пост";
            }

            // Создаем объект "Человек" для красивого стиля чата
            Person.Builder personBuilder = new Person.Builder().setName(senderName);
            if (avatarBitmap != null) {
                personBuilder.setIcon(IconCompat.createWithBitmap(avatarBitmap));
            }
            Person sender = personBuilder.build();

            // Стиль сообщения
            NotificationCompat.MessagingStyle messagingStyle = new NotificationCompat.MessagingStyle(sender)
                    .addMessage(messageText, message.getTimestamp(), sender);

            // Интент при клике (открыть чат с этим человеком)
            Intent intent = new Intent(this, ChatActivity.class);
            intent.putExtra("targetUserId", senderId);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

            // PendingIntent.FLAG_IMMUTABLE нужен для современных версий Android
            PendingIntent pendingIntent = PendingIntent.getActivity(this, senderId.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_chat) // ВАЖНО: Укажи тут свою белую иконку чата/логотипа (без фона)
                    .setStyle(messagingStyle)
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setColor(getResources().getColor(R.color.accent)) // Цвет значка
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setDefaults(NotificationCompat.DEFAULT_ALL) // Звук и вибрация по умолчанию
                    .setCategory(NotificationCompat.CATEGORY_MESSAGE);

            NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

            // Используем hashCode отправителя как ID уведомления.
            // Это значит, что все сообщения от одного человека будут заменять друг друга (или группироваться),
            // а не плодить 100 отдельных уведомлений.
            if (notificationManager != null) {
                notificationManager.notify(senderId.hashCode(), builder.build());
            }
        });
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Сообщения чата",
                    NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("Уведомления о новых сообщениях");
            channel.enableVibration(true);
            channel.setShowBadge(true); // Чтобы на иконке приложения была точка

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Тут по-хорошему надо отписывать слушатели, но так как сервис умирает, сборщик мусора справится
    }
}
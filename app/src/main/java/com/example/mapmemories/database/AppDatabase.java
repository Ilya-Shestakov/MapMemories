package com.example.mapmemories.database;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {OfflinePost.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    public abstract OfflinePostDao offlinePostDao();

    private static volatile AppDatabase INSTANCE;

    public static AppDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "map_memories_db")
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
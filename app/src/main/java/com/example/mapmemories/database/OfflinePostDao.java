package com.example.mapmemories.database;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import java.util.List;

@Dao
public interface OfflinePostDao {
    @Insert
    void insert(OfflinePost post);

    @Query("SELECT * FROM offline_posts")
    List<OfflinePost> getAllPostsSync(); // Для WorkManager

    @Query("SELECT * FROM offline_posts")
    LiveData<List<OfflinePost>> getAllPostsLive(); // Для иконки в MainActivity

    @Delete
    void delete(OfflinePost post);
}
package com.example.mapmemories;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.cachemanager.CacheManager;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;

public class Setting extends AppCompatActivity {

    private MapView mapView;
    private ExtendedFloatingActionButton fabDownloadMap;
    private ImageButton btnClose;

    // Настройки зума для скачивания
    // 10 - видно города, 16 - видно дома и тропинки.
    // Если ставить больше 17, размер будет огромным.
    private int minZoom = 10;
    private int maxZoom = 16;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Важно для OSMDroid
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_setting);

        // Инициализация View из твоего XML
        mapView = findViewById(R.id.mapView);
        fabDownloadMap = findViewById(R.id.fabDownloadMap);
        btnClose = findViewById(R.id.btnClose);

        // Настройка Toolbar (если нужно программно, хотя у тебя в XML он есть)
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayShowTitleEnabled(false); // Скрываем стандартный заголовок, у тебя свой TextView
        }

        setupMap();
        setupClickListeners();
    }

    private void setupMap() {
        mapView.setTileSource(new XYTileSource("Mapnik", 0, 19, 256, ".png",
                new String[]{"https://a.tile.openstreetmap.org/"}, "© OSM"));

        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false); // Убираем кнопки +/-, так красивее

        IMapController mapController = mapView.getController();
        mapController.setZoom(10.0);
        // Ставим центр (например, Москва, или можно брать текущую геолокацию)
        mapController.setCenter(new GeoPoint(55.7512, 37.6184));
    }

    private void setupClickListeners() {
        // Кнопка "Назад"
        btnClose.setOnClickListener(v -> {
            finish(); // Просто закрываем активити
        });

        // Кнопка "Загрузить"
        fabDownloadMap.setOnClickListener(v -> {
            showDownloadDialog();
        });
    }

    private void showDownloadDialog() {
        // 1. Получаем границы того, что сейчас на экране
        BoundingBox currentBox = mapView.getBoundingBox();

        // 2. Считаем количество тайлов (предварительно)
        CacheManager cacheManager = new CacheManager(mapView);
        int possibleTiles = cacheManager.possibleTilesInArea(currentBox, minZoom, maxZoom);

        // 3. Формируем сообщение
        String message = String.format(
                "Будет скачана область, видимая сейчас на экране.\n\n" +
                        "Уровни зума: %d - %d\n" +
                        "Количество тайлов: ~%d\n" +
                        "Примерный размер: ~%.1f МБ",
                minZoom, maxZoom, possibleTiles, (possibleTiles * 20.0) / 1024.0 // Считаем, что 1 тайл ~20КБ
        );

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Скачать эту область?");
        builder.setMessage(message);

        // Защита от дурака: если тайлов слишком много
        if (possibleTiles > 10000) {
            builder.setMessage(message + "\n\n⚠️ ВНИМАНИЕ: Область слишком большая! Загрузка займет много времени и памяти. Лучше приблизьте карту.");
            builder.setPositiveButton("Всё равно скачать", (dialog, which) -> startDownload(currentBox));
        } else {
            builder.setPositiveButton("Скачать", (dialog, which) -> startDownload(currentBox));
        }

        builder.setNegativeButton("Отмена", null);

        // Доп. кнопка для очистки кэша (если надо освободить место)
        builder.setNeutralButton("Очистить кэш тут", (dialog, which) -> clearCache(currentBox));

        builder.show();
    }

    private void startDownload(BoundingBox bbox) {
        CacheManager cacheManager = new CacheManager(mapView);

        Toast.makeText(this, "Загрузка началась...", Toast.LENGTH_SHORT).show();
        fabDownloadMap.setEnabled(false); // Блокируем кнопку пока качает
        fabDownloadMap.setText("Качаем...");

        // Используем метод с Callback, который есть в большинстве версий
        cacheManager.downloadAreaAsync(this, bbox, minZoom, maxZoom, new CacheManager.CacheManagerCallback() {
            @Override
            public void onTaskComplete() {
                runOnUiThread(() -> {
                    Toast.makeText(Setting.this, "Успешно скачано!", Toast.LENGTH_LONG).show();
                    fabDownloadMap.setEnabled(true);
                    fabDownloadMap.setText("Загрузить");
                });
            }

            @Override
            public void onTaskFailed(int errors) {
                runOnUiThread(() -> {
                    Toast.makeText(Setting.this, "Ошибка загрузки. Ошибок: " + errors, Toast.LENGTH_SHORT).show();
                    fabDownloadMap.setEnabled(true);
                    fabDownloadMap.setText("Загрузить");
                });
            }

            @Override public void updateProgress(int progress, int currentZoomLevel, int zoomMin, int zoomMax) {}
            @Override public void downloadStarted() {}
            @Override public void setPossibleTilesInArea(int total) {}
            // Если метод onTileDownloadSatisfied вызывает ошибку - просто удали его отсюда,
            // в старых версиях библиотеки его нет.
        });
    }

    private void clearCache(BoundingBox bbox) {
        CacheManager cacheManager = new CacheManager(mapView);
        // Используем версию без callback, чтобы не было ошибок компиляции
        cacheManager.cleanAreaAsync(this, bbox, minZoom, maxZoom);
        Toast.makeText(this, "Очистка кэша запущена...", Toast.LENGTH_SHORT).show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
    }
}
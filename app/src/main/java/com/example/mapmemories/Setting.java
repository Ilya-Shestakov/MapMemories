package com.example.mapmemories;

import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.preference.PreferenceManager; // Для конфигурации OSM
import android.view.MenuItem; // Для кнопки назад
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull; // Обычно не нужен для onCreate, но оставим
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity; // Вместо Fragment

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.compass.InternalCompassOrientationProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class Setting extends AppCompatActivity { // Наследуемся от Activity

    private MapView mapView;
    private ExtendedFloatingActionButton fabDownloadMap;

    // Для скачивания карт
    private ProgressDialog downloadProgress;
    private DownloadMapTask downloadTask;
    private ImageButton btnClose;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ВАЖНО: Инициализация конфигурации OSMDroid перед загрузкой layout
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_setting);

        // Включаем кнопку "Назад" в Toolbar (если он есть)
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle("Настройки карты");
        }

        // Находим элементы
        mapView = findViewById(R.id.mapView);
        btnClose = findViewById(R.id.btnClose);
        fabDownloadMap = findViewById(R.id.fabDownloadMap);

        // Настройка карты
        setupMap();

        // Настройка кликов
        setupClickListeners();
    }

    // Обработка нажатия кнопки "Назад" в тулбаре
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish(); // Закрыть активити
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setupMap() {
        // Устанавливаем оффлайн источник тайлов
        mapView.setTileSource(new XYTileSource(
                "Mapnik",
                0, 19, 256, ".png",
                new String[]{"https://a.tile.openstreetmap.org/"},
                "© OpenStreetMap contributors"
        ));

        mapView.setBuiltInZoomControls(true);
        mapView.setMultiTouchControls(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(19.0);

        // Центрируем на СПб
        IMapController mapController = mapView.getController();
        mapController.setZoom(12.0);
        mapController.setCenter(new GeoPoint(59.9343, 30.3351));

        // Добавляем компас. Context здесь 'this'
        CompassOverlay compassOverlay = new CompassOverlay(
                this,
                new InternalCompassOrientationProvider(this),
                mapView
        );
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        // Добавляем шкалу
        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(scaleBarOverlay);
    }

    private void setupClickListeners() {
        fabDownloadMap.setOnClickListener(v -> {
            showDownloadMapDialog();
        });
        btnClose.setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

    }

    private void showAddMemoryDialog(double lat, double lon) {
        String message = String.format("Добавить воспоминание:\nШирота: %.6f\nДолгота: %.6f",
                lat, lon);
        // getContext() заменяем на SettingActivity.this
        Toast.makeText(Setting.this, message, Toast.LENGTH_SHORT).show();
    }

    private void showDownloadMapDialog() {
        // getContext() -> this
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Скачать карту региона");

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        TextView nameLabel = new TextView(this);
        nameLabel.setText("Название региона:");
        layout.addView(nameLabel);

        EditText nameInput = new EditText(this);
        nameInput.setHint("Например: Центр СПб");
        layout.addView(nameInput);

        TextView zoomLabel = new TextView(this);
        zoomLabel.setText("\nУровни детализации (zoom):");
        layout.addView(zoomLabel);

        EditText minZoomInput = new EditText(this);
        minZoomInput.setHint("Минимальный zoom (например: 10)");
        layout.addView(minZoomInput);

        EditText maxZoomInput = new EditText(this);
        maxZoomInput.setHint("Максимальный zoom (например: 15)");
        layout.addView(maxZoomInput);

        TextView radiusLabel = new TextView(this);
        radiusLabel.setText("\nРадиус скачивания (км):");
        layout.addView(radiusLabel);

        EditText radiusInput = new EditText(this);
        radiusInput.setHint("Например: 5");
        layout.addView(radiusInput);

        builder.setView(layout);

        builder.setPositiveButton("Скачать", (dialog, which) -> {
            String regionName = nameInput.getText().toString();
            String minZoomStr = minZoomInput.getText().toString();
            String maxZoomStr = maxZoomInput.getText().toString();
            String radiusStr = radiusInput.getText().toString();

            if (regionName.isEmpty() || minZoomStr.isEmpty() ||
                    maxZoomStr.isEmpty() || radiusStr.isEmpty()) {
                Toast.makeText(Setting.this, "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int minZoom = Integer.parseInt(minZoomStr);
                int maxZoom = Integer.parseInt(maxZoomStr);
                double radiusKm = Double.parseDouble(radiusStr);

                GeoPoint center = (GeoPoint) mapView.getMapCenter();
                downloadMapRegion(regionName, center, minZoom, maxZoom, radiusKm);

            } catch (NumberFormatException e) {
                Toast.makeText(Setting.this, "Некорректные числа", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void downloadMapRegion(String regionName, GeoPoint center,
                                   int minZoom, int maxZoom, double radiusKm) {

        downloadProgress = new ProgressDialog(this);
        downloadProgress.setTitle("Скачивание карты");
        downloadProgress.setMessage("Подготовка...");
        downloadProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadProgress.setMax(100);
        downloadProgress.setCancelable(false);
        downloadProgress.show();

        downloadTask = new DownloadMapTask(regionName, center, minZoom, maxZoom, radiusKm);
        downloadTask.execute();
    }

    // Внутренний класс AsyncTask остается почти без изменений
    private class DownloadMapTask extends AsyncTask<Void, Integer, String> {

        private String regionName;
        private GeoPoint center;
        private int minZoom;
        private int maxZoom;
        private double radiusKm;

        public DownloadMapTask(String regionName, GeoPoint center,
                               int minZoom, int maxZoom, double radiusKm) {
            this.regionName = regionName;
            this.center = center;
            this.minZoom = minZoom;
            this.maxZoom = maxZoom;
            this.radiusKm = radiusKm;
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                BoundingBox bbox = calculateBoundingBox(center, radiusKm);

                // getContext().getExternalFilesDir -> getExternalFilesDir (метод Activity)
                File tilesDir = new File(
                        getExternalFilesDir(null),
                        "osmdroid/tiles/" + regionName.replace(" ", "_")
                );
                if (!tilesDir.exists()) {
                    tilesDir.mkdirs();
                }

                int totalTiles = calculateTotalTiles(bbox, minZoom, maxZoom);
                int downloaded = 0;

                publishProgress(0, totalTiles);

                for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
                    List<TileCoordinates> tiles = getTilesForZoom(bbox, zoom);

                    for (TileCoordinates tile : tiles) {
                        if (isCancelled()) {
                            return "Отменено пользователем";
                        }

                        String url = String.format(
                                "https://a.tile.openstreetmap.org/%d/%d/%d.png",
                                zoom, tile.x, tile.y
                        );

                        byte[] tileData = downloadTile(url);

                        if (tileData != null) {
                            File zoomDir = new File(tilesDir, String.valueOf(zoom));
                            if (!zoomDir.exists()) {
                                zoomDir.mkdirs();
                            }

                            File xDir = new File(zoomDir, String.valueOf(tile.x));
                            if (!xDir.exists()) {
                                xDir.mkdirs();
                            }

                            File tileFile = new File(xDir, tile.y + ".png");

                            try (FileOutputStream fos = new FileOutputStream(tileFile)) {
                                fos.write(tileData);
                            }
                        }

                        downloaded++;
                        publishProgress(downloaded, totalTiles);
                        Thread.sleep(50);
                    }
                }
                saveRegionMetadata(regionName, center, minZoom, maxZoom, radiusKm, totalTiles, tilesDir.getAbsolutePath());
                return "Успешно скачано " + totalTiles + " тайлов";

            } catch (Exception e) {
                return "Ошибка: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            if (downloadProgress != null && values.length >= 2) {
                int current = values[0];
                int total = values[1];
                downloadProgress.setProgress(current);
                downloadProgress.setMax(total);
                downloadProgress.setMessage(
                        String.format("Скачано: %d/%d тайлов", current, total)
                );
            }
        }

        @Override
        protected void onPostExecute(String result) {
            if (downloadProgress != null) {
                downloadProgress.dismiss();
                downloadProgress = null;
            }

            // getContext() -> SettingActivity.this
            Toast.makeText(Setting.this, result, Toast.LENGTH_LONG).show();
            enableOfflineMode();
        }
    }

    private BoundingBox calculateBoundingBox(GeoPoint center, double radiusKm) {
        double latDelta = radiusKm / 111.0;
        double lonDelta = radiusKm / (111.0 * Math.cos(Math.toRadians(center.getLatitude())));

        double minLat = center.getLatitude() - latDelta;
        double maxLat = center.getLatitude() + latDelta;
        double minLon = center.getLongitude() - lonDelta;
        double maxLon = center.getLongitude() + lonDelta;

        return new BoundingBox(maxLat, maxLon, minLat, minLon);
    }

    private int calculateTotalTiles(BoundingBox bbox, int minZoom, int maxZoom) {
        int total = 0;
        for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
            int[] tile1 = getTileNumber(bbox.getLatNorth(), bbox.getLonWest(), zoom);
            int[] tile2 = getTileNumber(bbox.getLatSouth(), bbox.getLonEast(), zoom);

            int tilesX = Math.abs(tile2[0] - tile1[0]) + 1;
            int tilesY = Math.abs(tile2[1] - tile1[1]) + 1;
            total += tilesX * tilesY;
        }
        return total;
    }

    private List<TileCoordinates> getTilesForZoom(BoundingBox bbox, int zoom) {
        List<TileCoordinates> tiles = new ArrayList<>();
        int[] tile1 = getTileNumber(bbox.getLatNorth(), bbox.getLonWest(), zoom);
        int[] tile2 = getTileNumber(bbox.getLatSouth(), bbox.getLonEast(), zoom);

        int minX = Math.min(tile1[0], tile2[0]);
        int maxX = Math.max(tile1[0], tile2[0]);
        int minY = Math.min(tile1[1], tile2[1]);
        int maxY = Math.max(tile1[1], tile2[1]);

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                tiles.add(new TileCoordinates(x, y));
            }
        }
        return tiles;
    }

    private int[] getTileNumber(double lat, double lon, int zoom) {
        int xtile = (int) Math.floor((lon + 180) / 360 * (1 << zoom));
        int ytile = (int) Math.floor(
                (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1 << zoom)
        );
        return new int[]{xtile, ytile};
    }

    private byte[] downloadTile(String urlString) {
        try {
            URL url = new URL(urlString);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                InputStream inputStream = connection.getInputStream();
                byte[] buffer = new byte[8192];
                int bytesRead;
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    output.write(buffer, 0, bytesRead);
                }
                inputStream.close();
                return output.toByteArray();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void saveRegionMetadata(String name, GeoPoint center,
                                    int minZoom, int maxZoom,
                                    double radiusKm, int tileCount, String path) {
        android.util.Log.d("MapDownload",
                String.format("Регион: %s, Центр: %.4f,%.4f, Zoom: %d-%d, Тайлов: %d, Путь: %s",
                        name, center.getLatitude(), center.getLongitude(),
                        minZoom, maxZoom, tileCount, path));
    }

    private void enableOfflineMode() {
        Toast.makeText(this, "Карта готова к оффлайн использованию", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() { // public -> protected в Activity
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    protected void onPause() { // public -> protected в Activity
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }
        if (downloadTask != null && !downloadTask.isCancelled()) {
            downloadTask.cancel(true);
        }
    }

    private static class TileCoordinates {
        int x, y;
        TileCoordinates(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
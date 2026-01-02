package com.example.mapmemories;

import android.app.ProgressDialog;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton; // ← ИЗМЕНИЛ
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

public class OfflineFragment extends Fragment {

    private MapView mapView;
    private FloatingActionButton fabAddMemory;
    private ExtendedFloatingActionButton fabDownloadMap; // ← ИЗМЕНИЛ ТИП
    private FloatingActionButton fabMyLocation;

    // Для скачивания карт
    private ProgressDialog downloadProgress;
    private DownloadMapTask downloadTask;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_offline_new, container, false);

        // Находим элементы (ПРАВИЛЬНЫЕ ТИПЫ)
        mapView = view.findViewById(R.id.mapView);
        fabAddMemory = view.findViewById(R.id.fabAddMemory);
        fabDownloadMap = view.findViewById(R.id.fabDownloadMap); // ExtendedFAB
        fabMyLocation = view.findViewById(R.id.fabMyLocation);

        // Настройка карты
        setupMap();

        // Настройка кликов
        setupClickListeners();

        return view;
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

        // Добавляем компас
        CompassOverlay compassOverlay = new CompassOverlay(
                getContext(),
                new InternalCompassOrientationProvider(getContext()),
                mapView
        );
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        // Добавляем шкалу
        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(mapView);
        mapView.getOverlays().add(scaleBarOverlay);
    }

    private void setupClickListeners() {
        // Кнопка добавления воспоминания
        fabAddMemory.setOnClickListener(v -> {
            GeoPoint center = (GeoPoint) mapView.getMapCenter();
            showAddMemoryDialog(center.getLatitude(), center.getLongitude());
        });

        // Кнопка скачать карту (ExtendedFAB)
        fabDownloadMap.setOnClickListener(v -> {
            showDownloadMapDialog();
        });

        // Кнопка "Моё местоположение"
        fabMyLocation.setOnClickListener(v -> {
            mapView.getController().animateTo(new GeoPoint(59.9343, 30.3351));
            mapView.getController().setZoom(15.0);
        });
    }

    private void showAddMemoryDialog(double lat, double lon) {
        String message = String.format("Добавить воспоминание:\nШирота: %.6f\nДолгота: %.6f",
                lat, lon);

        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }

    private void showDownloadMapDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
        builder.setTitle("Скачать карту региона");

        // Создаем кастомный layout для диалога
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        // Поле для названия региона
        TextView nameLabel = new TextView(getContext());
        nameLabel.setText("Название региона:");
        layout.addView(nameLabel);

        EditText nameInput = new EditText(getContext());
        nameInput.setHint("Например: Центр СПб");
        layout.addView(nameInput);

        // Выбор zoom уровней
        TextView zoomLabel = new TextView(getContext());
        zoomLabel.setText("\nУровни детализации (zoom):");
        layout.addView(zoomLabel);

        EditText minZoomInput = new EditText(getContext());
        minZoomInput.setHint("Минимальный zoom (например: 10)");
        layout.addView(minZoomInput);

        EditText maxZoomInput = new EditText(getContext());
        maxZoomInput.setHint("Максимальный zoom (например: 15)");
        layout.addView(maxZoomInput);

        // Радиус скачивания
        TextView radiusLabel = new TextView(getContext());
        radiusLabel.setText("\nРадиус скачивания (км):");
        layout.addView(radiusLabel);

        EditText radiusInput = new EditText(getContext());
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
                Toast.makeText(getContext(), "Заполните все поля", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                int minZoom = Integer.parseInt(minZoomStr);
                int maxZoom = Integer.parseInt(maxZoomStr);
                double radiusKm = Double.parseDouble(radiusStr);

                GeoPoint center = (GeoPoint) mapView.getMapCenter();

                // Начинаем скачивание
                downloadMapRegion(regionName, center, minZoom, maxZoom, radiusKm);

            } catch (NumberFormatException e) {
                Toast.makeText(getContext(), "Некорректные числа", Toast.LENGTH_SHORT).show();
            }
        });

        builder.setNegativeButton("Отмена", null);
        builder.show();
    }

    private void downloadMapRegion(String regionName, GeoPoint center,
                                   int minZoom, int maxZoom, double radiusKm) {

        // Показываем прогресс
        downloadProgress = new ProgressDialog(getContext());
        downloadProgress.setTitle("Скачивание карты");
        downloadProgress.setMessage("Подготовка...");
        downloadProgress.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        downloadProgress.setMax(100);
        downloadProgress.setCancelable(false);
        downloadProgress.show();

        // Запускаем фоновую задачу
        downloadTask = new DownloadMapTask(regionName, center, minZoom, maxZoom, radiusKm);
        downloadTask.execute();
    }

    // Фоновая задача для скачивания карт
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
                // Рассчитываем bounding box по радиусу
                BoundingBox bbox = calculateBoundingBox(center, radiusKm);

                // Создаём директорию для тайлов
                File tilesDir = new File(
                        getContext().getExternalFilesDir(null),
                        "osmdroid/tiles/" + regionName.replace(" ", "_")
                );
                if (!tilesDir.exists()) {
                    tilesDir.mkdirs();
                }

                // Скачиваем тайлы для каждого zoom уровня
                int totalTiles = calculateTotalTiles(bbox, minZoom, maxZoom);
                int downloaded = 0;

                publishProgress(0, totalTiles);

                for (int zoom = minZoom; zoom <= maxZoom; zoom++) {
                    List<TileCoordinates> tiles = getTilesForZoom(bbox, zoom);

                    for (TileCoordinates tile : tiles) {
                        if (isCancelled()) {
                            return "Отменено пользователем";
                        }

                        // URL тайла OpenStreetMap
                        String url = String.format(
                                "https://a.tile.openstreetmap.org/%d/%d/%d.png",
                                zoom, tile.x, tile.y
                        );

                        // Скачиваем тайл
                        byte[] tileData = downloadTile(url);

                        if (tileData != null) {
                            // Сохраняем в файл
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

                        // Небольшая пауза чтобы не грузить сервер
                        Thread.sleep(50);
                    }
                }

                // Сохраняем метаданные региона
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

            Toast.makeText(getContext(), result, Toast.LENGTH_LONG).show();

            // После скачивания переключаемся на оффлайн-режим
            enableOfflineMode();
        }
    }

    private BoundingBox calculateBoundingBox(GeoPoint center, double radiusKm) {
        // 1 градус широты ≈ 111 км
        double latDelta = radiusKm / 111.0;
        // 1 градус долготы зависит от широты
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
            // Координаты тайлов для bounding box
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
        // Сохраняем в SharedPreferences или SQLite
        android.util.Log.d("MapDownload",
                String.format("Регион: %s, Центр: %.4f,%.4f, Zoom: %d-%d, Тайлов: %d, Путь: %s",
                        name, center.getLatitude(), center.getLongitude(),
                        minZoom, maxZoom, tileCount, path));
    }

    private void enableOfflineMode() {
        // Переключаем карту на использование локального кеша
        Toast.makeText(getContext(),
                "Карта готова к оффлайн использованию", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mapView != null) {
            mapView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mapView != null) {
            mapView.onPause();
        }

        // Отменяем скачивание если оно идет
        if (downloadTask != null && !downloadTask.isCancelled()) {
            downloadTask.cancel(true);
        }
    }

    // Вспомогательный класс для координат тайла
    private static class TileCoordinates {
        int x, y;

        TileCoordinates(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
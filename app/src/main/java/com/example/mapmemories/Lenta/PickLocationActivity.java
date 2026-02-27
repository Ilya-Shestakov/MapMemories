package com.example.mapmemories.Lenta;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.LocationManager;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.mapmemories.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.MapEventsOverlay;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider;
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class PickLocationActivity extends AppCompatActivity {

    private MapView mapView;
    private ImageButton btnBack;
    private FloatingActionButton fabMyLocation;
    private MaterialButton btnConfirmLocation;
    private TextView tvSelectedAddress;

    private MyLocationNewOverlay myLocationOverlay;
    private Marker selectedMarker;
    private GeoPoint selectedPoint;
    private String selectedAddressText = "";

    private boolean isViewOnly = false; // Флаг режима просмотра

    private static final int REQUEST_LOCATION_PERMISSION = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_pick_location);

        initViews();

        // Читаем данные из Intent
        double startLat = getIntent().getDoubleExtra("lat", 0.0);
        double startLng = getIntent().getDoubleExtra("lng", 0.0);
        isViewOnly = getIntent().getBooleanExtra("viewOnly", false);

        setupMap(startLat, startLng);
        setupListeners();

        // Если это просмотр - настраиваем UI
        if (isViewOnly) {
            btnConfirmLocation.setVisibility(View.GONE);
            tvSelectedAddress.setText("Просмотр местоположения");
            // Можно скрыть кнопку поиска себя, если она не нужна в просмотре
            // fabMyLocation.setVisibility(View.GONE);
        }

        checkLocationPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
        if (hasPermissions() && myLocationOverlay != null) {
            myLocationOverlay.enableMyLocation();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
        if (myLocationOverlay != null) {
            myLocationOverlay.disableMyLocation();
            myLocationOverlay.disableFollowLocation();
        }
    }

    private void initViews() {
        mapView = findViewById(R.id.mapView);
        btnBack = findViewById(R.id.btnBack);
        fabMyLocation = findViewById(R.id.fabMyLocation);
        btnConfirmLocation = findViewById(R.id.btnConfirmLocation);
        tvSelectedAddress = findViewById(R.id.tvSelectedAddress);
    }

    private void setupMap(double startLat, double startLng) {
        mapView.setTileSource(new XYTileSource(
                "Mapnik", 0, 19, 256, ".png",
                new String[]{"https://a.tile.openstreetmap.org/"},
                "© OSM contributors"
        ));
        mapView.setMultiTouchControls(true);
        mapView.setBuiltInZoomControls(false);

        IMapController mapController = mapView.getController();

        // Логика установки начальной точки
        if (startLat != 0.0 && startLng != 0.0) {
            GeoPoint startPoint = new GeoPoint(startLat, startLng);
            mapController.setZoom(17.0);
            mapController.setCenter(startPoint);
            updateSelectedLocation(startPoint); // Ставим маркер сразу
        } else {
            // Если координат нет - Москва
            mapController.setZoom(15.0);
            mapController.setCenter(new GeoPoint(55.7558, 37.6173));
        }

        // Если НЕ только просмотр - разрешаем кликать по карте
        if (!isViewOnly) {
            MapEventsReceiver mReceive = new MapEventsReceiver() {
                @Override
                public boolean singleTapConfirmedHelper(GeoPoint p) {
                    updateSelectedLocation(p);
                    return true;
                }
                @Override
                public boolean longPressHelper(GeoPoint p) {
                    updateSelectedLocation(p);
                    return true;
                }
            };
            mapView.getOverlays().add(new MapEventsOverlay(mReceive));
        }

        // Слой "Где я"
        GpsMyLocationProvider provider = new GpsMyLocationProvider(this);
        provider.addLocationSource(LocationManager.GPS_PROVIDER);
        provider.addLocationSource(LocationManager.NETWORK_PROVIDER);

        myLocationOverlay = new MyLocationNewOverlay(provider, mapView);
        myLocationOverlay.setDrawAccuracyEnabled(true);

        // Если координат не было передано, при первом фиксе летим к юзеру
        if (startLat == 0.0 && startLng == 0.0) {
            myLocationOverlay.runOnFirstFix(() -> runOnUiThread(() -> {
                if (myLocationOverlay.getMyLocation() != null) {
                    mapView.getController().animateTo(myLocationOverlay.getMyLocation());
                    mapView.getController().setZoom(17.0);
                }
            }));
        }

        mapView.getOverlays().add(myLocationOverlay);
    }

    private void setupListeners() {
        btnBack.setOnClickListener(v -> finish());

        fabMyLocation.setOnClickListener(v -> {
            if (!hasPermissions()) {
                checkLocationPermissions();
                return;
            }
            if (!isLocationEnabled()) {
                showSettingsAlert();
                return;
            }
            if (myLocationOverlay.getMyLocation() != null) {
                mapView.getController().animateTo(myLocationOverlay.getMyLocation());
                mapView.getController().setZoom(17.0);
                myLocationOverlay.enableFollowLocation();
            } else {
                Toast.makeText(this, "Поиск спутников...", Toast.LENGTH_SHORT).show();
                myLocationOverlay.enableMyLocation();
            }
        });

        mapView.addMapListener(new org.osmdroid.events.MapListener() {
            @Override
            public boolean onScroll(org.osmdroid.events.ScrollEvent event) {
                if (myLocationOverlay.isFollowLocationEnabled()) {
                    myLocationOverlay.disableFollowLocation();
                }
                return true;
            }
            @Override
            public boolean onZoom(org.osmdroid.events.ZoomEvent event) { return true; }
        });

        btnConfirmLocation.setOnClickListener(v -> {
            if (selectedPoint != null) {
                Intent resultIntent = new Intent();
                resultIntent.putExtra("lat", selectedPoint.getLatitude());
                resultIntent.putExtra("lng", selectedPoint.getLongitude());
                resultIntent.putExtra("address", selectedAddressText);
                setResult(RESULT_OK, resultIntent);
                finish();
            }
        });
    }

    private void updateSelectedLocation(GeoPoint point) {
        selectedPoint = point;
        if (selectedMarker != null) mapView.getOverlays().remove(selectedMarker);

        selectedMarker = new Marker(mapView);
        selectedMarker.setPosition(point);
        selectedMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        selectedMarker.setTitle("Выбрано");
        mapView.getOverlays().add(selectedMarker);
        mapView.invalidate();

        btnConfirmLocation.setEnabled(true);
        getAddressFromLocation(point.getLatitude(), point.getLongitude());
    }

    private void getAddressFromLocation(double lat, double lng) {
        tvSelectedAddress.setText("Загрузка адреса...");
        new Thread(() -> {
            Geocoder geocoder = new Geocoder(PickLocationActivity.this, Locale.getDefault());
            String resultAddress = String.format("%.4f, %.4f", lat, lng);
            try {
                List<Address> addresses = geocoder.getFromLocation(lat, lng, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    if (address.getMaxAddressLineIndex() >= 0) {
                        resultAddress = address.getAddressLine(0);
                    } else {
                        resultAddress = address.getLocality();
                    }
                }
            } catch (IOException e) { e.printStackTrace(); }

            String finalResult = resultAddress;
            runOnUiThread(() -> {
                selectedAddressText = finalResult;
                tvSelectedAddress.setText(finalResult);
                if (selectedMarker != null) {
                    selectedMarker.setSnippet(finalResult);
                    selectedMarker.showInfoWindow();
                }
            });
        }).start();
    }

    private boolean hasPermissions() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void checkLocationPermissions() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                    REQUEST_LOCATION_PERMISSION);
        } else {
            if (!isLocationEnabled() && !isViewOnly) { // Предлагаем включить GPS только если мы не просто смотрим
                // showSettingsAlert();
            }
        }
    }

    private boolean isLocationEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);
    }

    private void showSettingsAlert() {
        new AlertDialog.Builder(this)
                .setTitle("Геолокация выключена")
                .setMessage("Для определения вашего местоположения необходимо включить GPS. Перейти в настройки?")
                .setPositiveButton("Настройки", (dialog, which) -> {
                    startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                })
                .setNegativeButton("Отмена", null)
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (myLocationOverlay != null) myLocationOverlay.enableMyLocation();
            }
        }
    }
}
package com.smartbus.driver.map;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.location.Location;
import com.smartbus.driver.BuildConfig;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.utils.Constants;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.osmdroid.config.Configuration;
import org.osmdroid.events.MapListener;
import org.osmdroid.events.ScrollEvent;
import org.osmdroid.events.ZoomEvent;
import org.osmdroid.tileprovider.tilesource.XYTileSource;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.Polyline;
import org.osmdroid.views.overlay.ScaleBarOverlay;
import org.osmdroid.views.overlay.compass.CompassOverlay;
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay;

/**
 * OpenStreetMap map controller (native equivalent of Leaflet
 * L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png')).
 */
public class MapManager {

    private static final int COLOR_ROUTE = 0xFF0B63CE;
    private static final int COLOR_TRAIL = 0x990B63CE;
    private static final int COLOR_SUCCESS = 0xFF19A65A;
    private static final int COLOR_WARNING = 0xFFE8952B;
    private static final int COLOR_ERROR = 0xFFC62828;
    private static final int COLOR_WHITE = 0xFFFFFFFF;
    private static final GeoPoint DEFAULT_CENTER = new GeoPoint(10.7769, 106.7009);

    private static final XYTileSource OPEN_STREET_MAP = new XYTileSource(
            "OpenStreetMap",
            1,
            19,
            256,
            ".png",
            new String[]{
                    "https://a.tile.openstreetmap.org/",
                    "https://b.tile.openstreetmap.org/",
                    "https://c.tile.openstreetmap.org/"
            },
            "© OpenStreetMap contributors"
    );

    private MapView mapView;
    private Marker vehicleMarker;
    private final List<Marker> stopMarkers = new ArrayList<>();
    private final Map<Long, Marker> stopMarkerById = new HashMap<>();
    private Polyline routePolyline;
    private Polyline trailPolyline;
    private final List<GeoPoint> trailPoints = new ArrayList<>();
    /** Only true after user taps "Định vị xe". */
    private boolean followVehicle = false;
    private boolean routeOverviewActive = false;
    private boolean hasFittedRouteOnce = false;
    private GeoPoint lastVehiclePosition;
    private Long currentStopId;
    private Long nextStopId;
    private List<StopModel> lastStops = new ArrayList<>();

    public static void initOsmConfiguration(Context context) {
        Context appContext = context.getApplicationContext();
        Configuration.getInstance().load(
                appContext,
                appContext.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
        );
        Configuration.getInstance().setUserAgentValue(
                BuildConfig.APPLICATION_ID + "/" + BuildConfig.VERSION_NAME
        );
        Configuration.getInstance().setTileDownloadThreads((short) 4);
        Configuration.getInstance().setCacheMapTileCount((short) 200);
    }

    public void attachMap(MapView mapView) {
        this.mapView = mapView;
        if (mapView == null) {
            return;
        }
        mapView.setTileSource(OPEN_STREET_MAP);
        mapView.setMultiTouchControls(true);
        mapView.setTilesScaledToDpi(true);
        mapView.setMinZoomLevel(3.0);
        mapView.setMaxZoomLevel(19.0);
        mapView.getController().setZoom((double) Constants.MAP_DEFAULT_ZOOM);
        mapView.getController().setCenter(DEFAULT_CENTER);
        mapView.getOverlayManager().getTilesOverlay().setEnabled(true);

        RotationGestureOverlay rotationOverlay = new RotationGestureOverlay(mapView);
        rotationOverlay.setEnabled(true);
        mapView.getOverlays().add(rotationOverlay);

        CompassOverlay compassOverlay = new CompassOverlay(mapView.getContext(), mapView);
        compassOverlay.enableCompass();
        mapView.getOverlays().add(compassOverlay);

        ScaleBarOverlay scaleBarOverlay = new ScaleBarOverlay(mapView);
        scaleBarOverlay.setAlignBottom(true);
        mapView.getOverlays().add(scaleBarOverlay);

        mapView.addMapListener(new MapListener() {
            @Override
            public boolean onScroll(ScrollEvent event) {
                if (event != null) {
                    followVehicle = false;
                }
                return false;
            }

            @Override
            public boolean onZoom(ZoomEvent event) {
                if (event != null) {
                    followVehicle = false;
                }
                return false;
            }
        });
        mapView.invalidate();
    }

    public void onResume() {
        if (mapView != null) {
            mapView.onResume();
        }
    }

    public void onPause() {
        if (mapView != null) {
            mapView.onPause();
        }
    }

    public void updateVehiclePosition(double latitude, double longitude) {
        if (mapView == null) {
            return;
        }
        GeoPoint position = new GeoPoint(latitude, longitude);
        float bearing = 0f;
        if (lastVehiclePosition != null) {
            float[] results = new float[2];
            Location.distanceBetween(
                    lastVehiclePosition.getLatitude(),
                    lastVehiclePosition.getLongitude(),
                    latitude,
                    longitude,
                    results
            );
            if (results[0] > 2f) {
                bearing = results[1];
                trailPoints.add(position);
                if (trailPoints.size() > 120) {
                    trailPoints.remove(0);
                }
                redrawTrail();
            }
        } else {
            trailPoints.add(position);
        }
        lastVehiclePosition = position;
        if (vehicleMarker == null) {
            vehicleMarker = new Marker(mapView);
            vehicleMarker.setTitle("Xe");
            vehicleMarker.setSnippet("Vị trí hiện tại");
            vehicleMarker.setIcon(createPinDrawable(COLOR_ROUTE, "X"));
            vehicleMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            vehicleMarker.setFlat(true);
            mapView.getOverlays().add(vehicleMarker);
        }
        vehicleMarker.setPosition(position);
        if (bearing != 0f) {
            vehicleMarker.setRotation(bearing);
        }
        // Never steal camera while user is viewing the full route / a stop.
        if (followVehicle && !routeOverviewActive) {
            mapView.getController().animateTo(position);
            mapView.getController().setZoom((double) Constants.MAP_DEFAULT_ZOOM);
        }
        mapView.invalidate();
    }

    public void showStops(List<StopModel> stops) {
        lastStops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
        redrawStops(false);
        if (!hasFittedRouteOnce && !lastStops.isEmpty()) {
            hasFittedRouteOnce = true;
            showFullRoute();
        }
    }

    public void highlightStops(Long currentStopId, Long nextStopId) {
        this.currentStopId = currentStopId;
        this.nextStopId = nextStopId;
        applyMarkerHighlights();
    }

    public void centerOnVehicle() {
        followVehicle = true;
        routeOverviewActive = false;
        if (mapView != null && lastVehiclePosition != null) {
            mapView.getController().animateTo(lastVehiclePosition);
            mapView.getController().setZoom((double) Constants.MAP_DEFAULT_ZOOM);
            mapView.invalidate();
        }
    }

    /** Fit camera to route stops only (do not include current vehicle if far away). */
    public void showFullRoute() {
        if (mapView == null) {
            return;
        }
        List<GeoPoint> points = collectRoutePoints();
        if (points.isEmpty()) {
            return;
        }
        followVehicle = false;
        routeOverviewActive = true;
        fitToPoints(points, true);
    }

    public void focusStop(StopModel stop) {
        if (mapView == null || stop == null
                || stop.getLatitude() == null || stop.getLongitude() == null) {
            return;
        }
        followVehicle = false;
        routeOverviewActive = true;
        GeoPoint point = new GeoPoint(stop.getLatitude(), stop.getLongitude());
        mapView.getController().animateTo(point);
        mapView.getController().setZoom(Math.max(Constants.MAP_DEFAULT_ZOOM, 16.5));
        Marker marker = stop.getId() != null ? stopMarkerById.get(stop.getId()) : null;
        if (marker != null) {
            marker.showInfoWindow();
        }
        mapView.invalidate();
    }

    public void clear() {
        clearStops();
        clearTrail();
        if (mapView != null && vehicleMarker != null) {
            mapView.getOverlays().remove(vehicleMarker);
        }
        vehicleMarker = null;
        lastVehiclePosition = null;
        currentStopId = null;
        nextStopId = null;
        lastStops.clear();
        followVehicle = false;
        routeOverviewActive = false;
        hasFittedRouteOnce = false;
        mapView = null;
    }

    private void redrawStops(boolean moveCamera) {
        if (mapView == null) {
            return;
        }
        clearStops();
        if (lastStops.isEmpty()) {
            return;
        }

        for (StopModel stop : lastStops) {
            if (stop.getLatitude() == null || stop.getLongitude() == null) {
                continue;
            }
            GeoPoint point = new GeoPoint(stop.getLatitude(), stop.getLongitude());
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle(stop.getName());
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            applyStopStyle(marker, stop.getId());
            mapView.getOverlays().add(marker);
            stopMarkers.add(marker);
            if (stop.getId() != null) {
                stopMarkerById.put(stop.getId(), marker);
            }
        }

        List<GeoPoint> routePoints = collectRoutePoints();
        redrawRoutePolyline(routePoints);
        if (moveCamera && !routePoints.isEmpty() && !routeOverviewActive && !followVehicle) {
            fitToPoints(routePoints, false);
        }
        mapView.invalidate();
    }

    private List<GeoPoint> collectRoutePoints() {
        List<GeoPoint> points = new ArrayList<>();
        for (Marker marker : stopMarkers) {
            if (marker.getPosition() != null) {
                points.add(marker.getPosition());
            }
        }
        if (points.isEmpty()) {
            for (StopModel stop : lastStops) {
                if (stop.getLatitude() != null && stop.getLongitude() != null) {
                    points.add(new GeoPoint(stop.getLatitude(), stop.getLongitude()));
                }
            }
        }
        return points;
    }

    private void fitToPoints(List<GeoPoint> points, boolean animated) {
        if (mapView == null || points == null || points.isEmpty()) {
            return;
        }
        if (points.size() == 1) {
            if (animated) {
                mapView.getController().animateTo(points.get(0));
            } else {
                mapView.getController().setCenter(points.get(0));
            }
            mapView.getController().setZoom((double) Constants.MAP_DEFAULT_ZOOM);
            mapView.invalidate();
            return;
        }
        BoundingBox box = BoundingBox.fromGeoPoints(points).increaseByScale(1.35f);
        mapView.post(() -> {
            if (mapView != null) {
                mapView.zoomToBoundingBox(box, animated, 100);
                mapView.invalidate();
            }
        });
    }

    private void applyStopStyle(Marker marker, Long stopId) {
        StopModel stop = findStopById(stopId);
        int index = stop != null ? lastStops.indexOf(stop) : -1;
        if (stopId != null && stopId.equals(currentStopId)) {
            marker.setSnippet("Bến hiện tại");
            marker.setIcon(createPinDrawable(COLOR_SUCCESS, "HT"));
        } else if (stopId != null && stopId.equals(nextStopId)) {
            marker.setSnippet("Bến tiếp theo");
            marker.setIcon(createPinDrawable(COLOR_ROUTE, "TT"));
        } else if (index == 0) {
            marker.setSnippet("Bến đầu tuyến");
            marker.setIcon(createPinDrawable(COLOR_SUCCESS, "Đ"));
        } else if (index == lastStops.size() - 1) {
            marker.setSnippet("Bến cuối tuyến");
            marker.setIcon(createPinDrawable(COLOR_ERROR, "C"));
        } else {
            marker.setSnippet("Bến");
            marker.setIcon(createPinDrawable(COLOR_WARNING, index >= 0 ? String.valueOf(index + 1) : "B"));
        }
    }

    private StopModel findStopById(Long stopId) {
        if (stopId == null) {
            return null;
        }
        for (StopModel stop : lastStops) {
            if (stopId.equals(stop.getId())) {
                return stop;
            }
        }
        return null;
    }

    private Drawable createPinDrawable(int color, String text) {
        int width = 72;
        int height = 90;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Path path = new Path();
        path.moveTo(width / 2f, height - 4f);
        path.cubicTo(width * 0.15f, height * 0.55f, 8f, height * 0.38f, 8f, height * 0.25f);
        path.cubicTo(8f, 8f, width - 8f, 8f, width - 8f, height * 0.25f);
        path.cubicTo(width - 8f, height * 0.38f, width * 0.85f, height * 0.55f, width / 2f, height - 4f);
        paint.setColor(color);
        canvas.drawPath(path, paint);
        paint.setColor(COLOR_WHITE);
        canvas.drawCircle(width / 2f, 28f, 21f, paint);
        paint.setColor(color);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextSize(text.length() > 2 ? 17f : 20f);
        canvas.drawText(text, width / 2f, 35f, paint);
        return new BitmapDrawable(mapView.getResources(), bitmap);
    }

    private void applyMarkerHighlights() {
        for (Map.Entry<Long, Marker> entry : stopMarkerById.entrySet()) {
            Marker marker = entry.getValue();
            if (marker != null) {
                applyStopStyle(marker, entry.getKey());
            }
        }
        if (mapView != null) {
            mapView.invalidate();
        }
    }

    private void redrawTrail() {
        if (mapView == null || trailPoints.size() < 2) {
            return;
        }
        if (trailPolyline != null) {
            mapView.getOverlays().remove(trailPolyline);
        }
        trailPolyline = new Polyline(mapView);
        trailPolyline.setPoints(new ArrayList<>(trailPoints));
        trailPolyline.getOutlinePaint().setColor(COLOR_TRAIL);
        trailPolyline.getOutlinePaint().setStrokeWidth(6f);
        mapView.getOverlays().add(0, trailPolyline);
        mapView.invalidate();
    }

    private void redrawRoutePolyline(List<GeoPoint> routePoints) {
        if (mapView == null) {
            return;
        }
        if (routePolyline != null) {
            mapView.getOverlays().remove(routePolyline);
            routePolyline = null;
        }
        if (routePoints == null || routePoints.size() < 2) {
            return;
        }
        routePolyline = new Polyline(mapView);
        routePolyline.setPoints(new ArrayList<>(routePoints));
        routePolyline.getOutlinePaint().setColor(COLOR_ROUTE);
        routePolyline.getOutlinePaint().setStrokeWidth(8f);
        routePolyline.getOutlinePaint().setAlpha(170);
        mapView.getOverlays().add(0, routePolyline);
    }

    private void clearTrail() {
        trailPoints.clear();
        if (mapView != null && trailPolyline != null) {
            mapView.getOverlays().remove(trailPolyline);
        }
        trailPolyline = null;
    }

    private void clearStops() {
        if (mapView != null && routePolyline != null) {
            mapView.getOverlays().remove(routePolyline);
        }
        routePolyline = null;
        if (mapView != null) {
            for (Marker marker : stopMarkers) {
                mapView.getOverlays().remove(marker);
            }
        }
        stopMarkers.clear();
        stopMarkerById.clear();
    }
}

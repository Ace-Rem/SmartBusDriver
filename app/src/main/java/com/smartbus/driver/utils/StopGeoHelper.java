package com.smartbus.driver.utils;

import android.location.Location;
import com.smartbus.driver.model.StopModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Client-side stop helpers so dashboard can show nearest/next even when
 * backend has not yet persisted currentStop (or old deploy still uses 50m gate).
 */
public final class StopGeoHelper {

    private StopGeoHelper() {
    }

    public static StopModel findNearest(List<StopModel> stops, double latitude, double longitude) {
        if (stops == null || stops.isEmpty()) {
            return null;
        }
        StopModel nearest = null;
        float best = Float.MAX_VALUE;
        float[] results = new float[1];
        for (StopModel stop : stops) {
            if (stop == null || stop.getLatitude() == null || stop.getLongitude() == null) {
                continue;
            }
            Location.distanceBetween(
                    latitude,
                    longitude,
                    stop.getLatitude(),
                    stop.getLongitude(),
                    results
            );
            if (results[0] < best) {
                best = results[0];
                nearest = stop;
            }
        }
        return nearest;
    }

    public static StopModel findCurrentAlongRoute(
            List<StopModel> stops,
            StopModel previousCurrent,
            double latitude,
            double longitude
    ) {
        List<StopModel> ordered = sortedByOrder(stops);
        if (ordered.isEmpty()) {
            return null;
        }
        int previousOrder = previousCurrent == null || previousCurrent.getStopOrder() == null
                ? Integer.MIN_VALUE
                : previousCurrent.getStopOrder();
        StopModel nearestAhead = null;
        float bestAhead = Float.MAX_VALUE;
        StopModel nearestAny = null;
        float bestAny = Float.MAX_VALUE;
        float[] results = new float[1];
        for (StopModel stop : ordered) {
            if (stop == null || stop.getLatitude() == null || stop.getLongitude() == null) {
                continue;
            }
            Location.distanceBetween(
                    latitude,
                    longitude,
                    stop.getLatitude(),
                    stop.getLongitude(),
                    results
            );
            if (results[0] < bestAny) {
                bestAny = results[0];
                nearestAny = stop;
            }
            if (stop.getStopOrder() != null && stop.getStopOrder() >= previousOrder
                    && results[0] < bestAhead) {
                bestAhead = results[0];
                nearestAhead = stop;
            }
        }
        if (nearestAhead == null) {
            return nearestAny;
        }
        if (previousCurrent != null
                && previousCurrent.getStopOrder() != null
                && nearestAny != null
                && nearestAny.getStopOrder() != null
                && nearestAny.getStopOrder() < previousCurrent.getStopOrder()
                && bestAny + 80f < bestAhead) {
            return previousCurrent;
        }
        return nearestAhead;
    }

    public static StopModel findNext(List<StopModel> stops, StopModel current) {
        if (stops == null || current == null || current.getStopOrder() == null) {
            return null;
        }
        StopModel next = null;
        for (StopModel stop : stops) {
            if (stop == null || stop.getStopOrder() == null) {
                continue;
            }
            if (stop.getStopOrder() <= current.getStopOrder()) {
                continue;
            }
            if (next == null || stop.getStopOrder() < next.getStopOrder()) {
                next = stop;
            }
        }
        return next;
    }

    public static StopModel findById(List<StopModel> stops, Long stopId) {
        if (stops == null || stopId == null) {
            return null;
        }
        for (StopModel stop : stops) {
            if (stop != null && stopId.equals(stop.getId())) {
                return stop;
            }
        }
        return null;
    }

    public static List<StopModel> sortedByOrder(List<StopModel> stops) {
        if (stops == null || stops.isEmpty()) {
            return Collections.emptyList();
        }
        List<StopModel> copy = new ArrayList<>(stops);
        Collections.sort(copy, Comparator.comparing(
                stop -> stop.getStopOrder() == null ? Integer.MAX_VALUE : stop.getStopOrder()
        ));
        return copy;
    }
}

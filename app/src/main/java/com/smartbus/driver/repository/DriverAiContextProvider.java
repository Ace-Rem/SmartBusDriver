package com.smartbus.driver.repository;

import com.smartbus.driver.database.EntityMapper;
import com.smartbus.driver.database.PassengerRecordEntity;
import com.smartbus.driver.database.RouteEntity;
import com.smartbus.driver.database.SmartBusDatabase;
import com.smartbus.driver.database.StopEntity;
import com.smartbus.driver.database.TripEntity;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import com.smartbus.driver.utils.Constants;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Builds the driver's complete current-trip context from Room only. */
public final class DriverAiContextProvider {

    private final SmartBusDatabase database;

    public DriverAiContextProvider(SmartBusDatabase database) {
        this.database = database;
    }

    public Map<String, Object> read() {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("app", "smartbus-driver");
        TripEntity entity = database.tripDao().findByStatus(Constants.TRIP_STATUS_IN_PROGRESS);
        if (entity == null) {
            context.put("selectionState", "NO_SELECTION");
            context.put("tripStatus", "NOT_STARTED");
            context.put("hasCurrentTrip", false);
            context.put("stops", new ArrayList<>());
            context.put("passengerGroups", new ArrayList<>());
            context.put("passengerTotal", 0);
            return context;
        }

        TripModel trip = EntityMapper.toTripModel(entity);
        RouteModel route = EntityMapper.toRouteModel(database.routeDao().findById(entity.routeId));
        List<StopModel> stops = EntityMapper.toStopModels(database.stopDao().findByRouteId(entity.routeId));
        StopModel current = findCurrentStop(stops, entity);
        StopModel next = nextStop(stops, current);
        List<PassengerRecordEntity> records = database.passengerRecordDao().findByTripId(entity.id);

        context.put("selectionState", route == null || stops.isEmpty()
                ? "PARTIAL_LOCAL_DATA" : "FULL_LOCAL_DATA");
        context.put("contextSource", "ROOM");
        context.put("hasCurrentTrip", true);
        context.put("localOnlyTrip", entity.id >= 1_000_000_000_000L);
        context.put("currentTripId", trip.getId());
        context.put("selectedTripId", trip.getId());
        context.put("currentTripName", "Chuyến #" + trip.getId());
        context.put("currentTripCode", "#" + trip.getId());
        context.put("tripStatus", trip.getStatus());
        context.put("tripId", trip.getId());
        context.put("status", trip.getStatus());
        context.put("selectedRouteId", route == null ? trip.getRouteId() : route.getId());
        context.put("currentRouteId", route == null ? trip.getRouteId() : route.getId());
        context.put("selectedRouteName", route == null ? null : route.getName());
        context.put("currentRouteName", route == null ? null : route.getName());
        context.put("selectedRouteCode", route == null ? null : route.getCode());
        context.put("selectedRouteLabel", routeLabel(route));
        context.put("routeName", route == null ? null : route.getName());
        context.put("routeCode", route == null ? null : route.getCode());
        context.put("startedAt", trip.getStartedAt());
        putStop(context, "currentStop", current);
        putStop(context, "nextStop", next);
        context.put("currentStopId", current == null ? entity.currentStopId : current.getId());
        context.put("nextStopId", next == null ? null : next.getId());
        context.put("remainingStopsCount", remainingStops(stops, current));
        context.put("totalStopsOnRoute", stops.size());
        context.put("currentLatitude", trip.getCurrentLatitude());
        context.put("currentLongitude", trip.getCurrentLongitude());
        // Explicit vehicle aliases prevent the passenger's own GPS snapshot
        // from ever being mistaken for the bus position by Chat AI.
        context.put("vehicleLatitude", trip.getCurrentLatitude());
        context.put("vehicleLongitude", trip.getCurrentLongitude());
        context.put("vehicleCurrentStopId", current == null ? entity.currentStopId : current.getId());
        context.put("vehicleCurrentStopName", current == null ? null : current.getName());
        context.put("vehicleCurrentStopOrder", current == null ? null : current.getStopOrder());
        context.put("vehicleNextStopId", next == null ? null : next.getId());
        context.put("vehicleNextStopName", next == null ? null : next.getName());
        context.put("vehicleNextStopOrder", next == null ? null : next.getStopOrder());
        context.put("tripStartedAt", trip.getStartedAt());
        context.put("tripEndedAt", trip.getEndedAt());
        context.put("stops", stopList(stops));
        context.put("stopsOnRoute", stopNames(stops));

        int total = 0;
        int onboard = 0;
        int onboardGroups = 0;
        List<Map<String, Object>> groups = new ArrayList<>();
        for (PassengerRecordEntity record : records) {
            int count = Math.max(0, record.passengerCount);
            total += count;
            if ("ON_BOARD".equals(record.status)) {
                onboard += count;
                onboardGroups++;
            }
            Map<String, Object> group = new LinkedHashMap<>();
            group.put("recordId", record.id);
            group.put("passengerIdentifier", record.passengerIdentifier);
            group.put("boardingStopId", record.boardingStopId);
            group.put("destinationStopId", record.destinationStopId);
            group.put("passengerCount", count);
            group.put("status", record.status);
            group.put("source", record.source);
            groups.add(group);
        }
        // The dashboard counter represents passengers currently on board,
        // not pending requests. Keep the raw record total separately for
        // diagnostics so Chat AI cannot mistake a request for a passenger.
        context.put("passengerRecordTotal", total);
        context.put("passengerTotal", onboard);
        context.put("totalPassengers", onboard);
        context.put("passengerTotalOnBoard", onboard);
        context.put("passengerGroupCount", onboardGroups);
        context.put("passengerRecordGroupCount", groups.size());
        context.put("passengerGroups", groups);
        context.put("checkInCount", onboardGroups);
        return context;
    }

    private int remainingStops(List<StopModel> stops, StopModel current) {
        if (current == null || current.getStopOrder() == null) return stops.size();
        int count = 0;
        for (StopModel stop : stops) {
            if (stop.getStopOrder() != null && stop.getStopOrder() > current.getStopOrder()) count++;
        }
        return count;
    }

    private StopModel findCurrentStop(List<StopModel> stops, TripEntity trip) {
        if (trip.currentStopId != null) {
            for (StopModel stop : stops) {
                if (trip.currentStopId.equals(stop.getId())) return stop;
            }
        }
        if (trip.currentLatitude == null || trip.currentLongitude == null) return null;
        StopModel nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (StopModel stop : stops) {
            if (stop.getLatitude() == null || stop.getLongitude() == null) continue;
            double lat = trip.currentLatitude - stop.getLatitude();
            double lng = trip.currentLongitude - stop.getLongitude();
            double distance = lat * lat + lng * lng;
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = stop;
            }
        }
        return nearest;
    }

    private StopModel nextStop(List<StopModel> stops, StopModel current) {
        if (current == null || current.getStopOrder() == null) return null;
        StopModel next = null;
        for (StopModel stop : stops) {
            if (stop.getStopOrder() != null && stop.getStopOrder() > current.getStopOrder()
                    && (next == null || stop.getStopOrder() < next.getStopOrder())) next = stop;
        }
        return next;
    }

    private void putStop(Map<String, Object> context, String prefix, StopModel stop) {
        context.put(prefix + "Id", stop == null ? null : stop.getId());
        context.put(prefix + "Name", stop == null ? null : stop.getName());
        context.put(prefix + "Order", stop == null ? null : stop.getStopOrder());
        context.put(prefix + "Latitude", stop == null ? null : stop.getLatitude());
        context.put(prefix + "Longitude", stop == null ? null : stop.getLongitude());
    }

    private List<Map<String, Object>> stopList(List<StopModel> stops) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (StopModel stop : stops) {
            Map<String, Object> item = new LinkedHashMap<>();
            putStop(item, "stop", stop);
            result.add(item);
        }
        return result;
    }

    private String stopNames(List<StopModel> stops) {
        StringBuilder result = new StringBuilder();
        for (StopModel stop : stops) {
            if (result.length() > 0) result.append(" | ");
            result.append(stop.getStopOrder() == null ? "?" : stop.getStopOrder())
                    .append(". ").append(stop.getName());
        }
        return result.toString();
    }

    private String routeLabel(RouteModel route) {
        if (route == null) return null;
        String code = route.getCode() == null ? "" : route.getCode();
        String name = route.getName() == null ? "" : route.getName();
        return code.isBlank() ? name : (name.isBlank() ? code : code + " - " + name);
    }
}

package com.smartbus.driver.database;

import com.smartbus.driver.model.DriverModel;
import com.smartbus.driver.model.PassengerRecordModel;
import com.smartbus.driver.model.RouteModel;
import com.smartbus.driver.model.StopModel;
import com.smartbus.driver.model.TripModel;
import java.util.ArrayList;
import java.util.List;

public final class EntityMapper {

    private EntityMapper() {
    }

    public static DriverEntity toEntity(DriverModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        DriverEntity entity = new DriverEntity();
        entity.id = model.getId();
        entity.fullName = model.getFullName();
        entity.phoneNumber = model.getPhoneNumber();
        entity.licenseNumber = model.getLicenseNumber();
        return entity;
    }

    public static DriverModel toDriverModel(DriverEntity entity) {
        if (entity == null) {
            return null;
        }
        DriverModel model = new DriverModel();
        model.setId(entity.id);
        model.setFullName(entity.fullName);
        model.setPhoneNumber(entity.phoneNumber);
        model.setLicenseNumber(entity.licenseNumber);
        return model;
    }

    public static RouteEntity toEntity(RouteModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        RouteEntity entity = new RouteEntity();
        entity.id = model.getId();
        entity.code = model.getCode();
        entity.name = model.getName();
        entity.description = model.getDescription();
        return entity;
    }

    public static RouteModel toRouteModel(RouteEntity entity) {
        if (entity == null) {
            return null;
        }
        RouteModel model = new RouteModel();
        model.setId(entity.id);
        model.setCode(entity.code);
        model.setName(entity.name);
        model.setDescription(entity.description);
        return model;
    }

    public static List<RouteModel> toRouteModels(List<RouteEntity> entities) {
        List<RouteModel> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (RouteEntity entity : entities) {
            models.add(toRouteModel(entity));
        }
        return models;
    }

    public static StopEntity toEntity(StopModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        StopEntity entity = new StopEntity();
        entity.id = model.getId();
        entity.routeId = model.getRouteId() != null ? model.getRouteId() : 0L;
        entity.name = model.getName();
        entity.latitude = model.getLatitude() != null ? model.getLatitude() : 0d;
        entity.longitude = model.getLongitude() != null ? model.getLongitude() : 0d;
        entity.stopOrder = model.getStopOrder() != null ? model.getStopOrder() : 0;
        return entity;
    }

    public static StopModel toStopModel(StopEntity entity) {
        if (entity == null) {
            return null;
        }
        StopModel model = new StopModel();
        model.setId(entity.id);
        model.setRouteId(entity.routeId);
        model.setName(entity.name);
        model.setLatitude(entity.latitude);
        model.setLongitude(entity.longitude);
        model.setStopOrder(entity.stopOrder);
        return model;
    }

    public static List<StopModel> toStopModels(List<StopEntity> entities) {
        List<StopModel> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (StopEntity entity : entities) {
            models.add(toStopModel(entity));
        }
        return models;
    }

    public static TripEntity toEntity(TripModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        TripEntity entity = new TripEntity();
        entity.id = model.getId();
        entity.driverId = model.getDriverId() != null ? model.getDriverId() : 0L;
        entity.routeId = model.getRouteId() != null ? model.getRouteId() : 0L;
        entity.currentStopId = model.getCurrentStopId();
        entity.status = model.getStatus();
        entity.startedAt = model.getStartedAt();
        entity.endedAt = model.getEndedAt();
        entity.currentLatitude = model.getCurrentLatitude();
        entity.currentLongitude = model.getCurrentLongitude();
        return entity;
    }

    public static TripModel toTripModel(TripEntity entity) {
        if (entity == null) {
            return null;
        }
        TripModel model = new TripModel();
        model.setId(entity.id);
        model.setDriverId(entity.driverId);
        model.setRouteId(entity.routeId);
        model.setCurrentStopId(entity.currentStopId);
        model.setStatus(entity.status);
        model.setStartedAt(entity.startedAt);
        model.setEndedAt(entity.endedAt);
        model.setCurrentLatitude(entity.currentLatitude);
        model.setCurrentLongitude(entity.currentLongitude);
        return model;
    }

    public static List<TripModel> toTripModels(List<TripEntity> entities) {
        List<TripModel> models = new ArrayList<>();
        if (entities == null) {
            return models;
        }
        for (TripEntity entity : entities) {
            models.add(toTripModel(entity));
        }
        return models;
    }

    public static PassengerRecordEntity toEntity(PassengerRecordModel model) {
        if (model == null || model.getId() == null) {
            return null;
        }
        PassengerRecordEntity entity = new PassengerRecordEntity();
        entity.id = model.getId();
        entity.tripId = model.getTripId() != null ? model.getTripId() : 0L;
        entity.stopId = model.getStopId();
        entity.passengerCount = model.getPassengerCount() != null ? model.getPassengerCount() : 0;
        entity.recordedAt = model.getRecordedAt();
        entity.note = model.getNote();
        return entity;
    }

    public static PassengerRecordModel toPassengerRecordModel(PassengerRecordEntity entity) {
        if (entity == null) {
            return null;
        }
        PassengerRecordModel model = new PassengerRecordModel();
        model.setId(entity.id);
        model.setTripId(entity.tripId);
        model.setStopId(entity.stopId);
        model.setPassengerCount(entity.passengerCount);
        model.setRecordedAt(entity.recordedAt);
        model.setNote(entity.note);
        return model;
    }
}

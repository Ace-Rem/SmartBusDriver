package com.smartbus.driver.model;

import java.util.List;

public class BootstrapModel {
    private String serverTime;
    private List<RouteModel> routes;
    private List<StopModel> stops;
    private List<TripModel> activeTrips;

    public String getServerTime() {
        return serverTime;
    }

    public void setServerTime(String serverTime) {
        this.serverTime = serverTime;
    }

    public List<RouteModel> getRoutes() {
        return routes;
    }

    public void setRoutes(List<RouteModel> routes) {
        this.routes = routes;
    }

    public List<StopModel> getStops() {
        return stops;
    }

    public void setStops(List<StopModel> stops) {
        this.stops = stops;
    }

    public List<TripModel> getActiveTrips() {
        return activeTrips;
    }

    public void setActiveTrips(List<TripModel> activeTrips) {
        this.activeTrips = activeTrips;
    }
}

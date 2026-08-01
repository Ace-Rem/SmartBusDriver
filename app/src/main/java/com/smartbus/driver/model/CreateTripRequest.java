package com.smartbus.driver.model;

public class CreateTripRequest {

    private Long routeId;

    public CreateTripRequest() {
    }

    public CreateTripRequest(Long routeId) {
        this.routeId = routeId;
    }

    public Long getRouteId() {
        return routeId;
    }

    public void setRouteId(Long routeId) {
        this.routeId = routeId;
    }
}

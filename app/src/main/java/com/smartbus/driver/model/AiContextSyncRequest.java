package com.smartbus.driver.model;

import java.util.Map;

public class AiContextSyncRequest {
    private Long tripId;
    private Long routeId;
    private Map<String, Object> context;
    private Long clientVersion;

    public AiContextSyncRequest(Long tripId, Long routeId, Map<String, Object> context, Long clientVersion) {
        this.tripId = tripId;
        this.routeId = routeId;
        this.context = context;
        this.clientVersion = clientVersion;
    }
}

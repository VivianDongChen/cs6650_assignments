package com.cs6650.chat.consumer.health;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

/**
 * Health status response model.
 */
public class HealthStatus {
    @JsonProperty("status")
    private String status;  // "healthy" or "unhealthy"

    @JsonProperty("timestamp")
    private long timestamp;

    @JsonProperty("components")
    private Map<String, ComponentHealth> components;

    @JsonProperty("metrics")
    private Map<String, Object> metrics;

    public HealthStatus() {
        this.components = new HashMap<>();
        this.metrics = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public Map<String, ComponentHealth> getComponents() {
        return components;
    }

    public void setComponents(Map<String, ComponentHealth> components) {
        this.components = components;
    }

    public Map<String, Object> getMetrics() {
        return metrics;
    }

    public void setMetrics(Map<String, Object> metrics) {
        this.metrics = metrics;
    }

    public void addComponent(String name, ComponentHealth health) {
        this.components.put(name, health);
    }

    public void addMetric(String name, Object value) {
        this.metrics.put(name, value);
    }

    public static class ComponentHealth {
        @JsonProperty("status")
        private String status;

        @JsonProperty("message")
        private String message;

        public ComponentHealth() {
        }

        public ComponentHealth(String status, String message) {
            this.status = status;
            this.message = message;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}

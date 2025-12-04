package com.cs6650.chat.consumer.health;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.queue.MessageConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Health check servlet for monitoring consumer application health.
 * Exposes /health endpoint that returns JSON health status.
 */
public class HealthCheckServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthCheckServlet.class);
    private final MessageConsumer messageConsumer;
    private final ObjectMapper objectMapper;

    public HealthCheckServlet(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
        this.objectMapper = new ObjectMapper();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        try {
            HealthStatus healthStatus = checkHealth();

            // Set response status code
            if ("healthy".equals(healthStatus.getStatus())) {
                resp.setStatus(HttpServletResponse.SC_OK);
            } else {
                resp.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
            }

            // Write JSON response
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(resp.getWriter(), healthStatus);

            LOGGER.debug("Health check completed: {}", healthStatus.getStatus());
        } catch (Exception e) {
            LOGGER.error("Health check failed", e);
            resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            resp.setContentType("application/json");
            resp.getWriter().write("{\"status\":\"unhealthy\",\"error\":\"" + e.getMessage() + "\"}");
        }
    }

    /**
     * Perform health checks on all components.
     */
    private HealthStatus checkHealth() {
        HealthStatus healthStatus = new HealthStatus();
        boolean allHealthy = true;

        // Check RoomManager
        try {
            RoomManager roomManager = messageConsumer.getRoomManager();
            if (roomManager != null) {
                healthStatus.addComponent("roomManager",
                    new HealthStatus.ComponentHealth("healthy", "RoomManager operational"));

                // Add metrics
                healthStatus.addMetric("totalSessions", roomManager.getTotalSessions());
                healthStatus.addMetric("messagesProcessed", roomManager.getMessagesProcessed());
                healthStatus.addMetric("duplicatesDetected", roomManager.getDuplicatesDetected());
                healthStatus.addMetric("broadcastsSucceeded", roomManager.getBroadcastsSucceeded());
                healthStatus.addMetric("broadcastsFailed", roomManager.getBroadcastsFailed());
            } else {
                healthStatus.addComponent("roomManager",
                    new HealthStatus.ComponentHealth("unhealthy", "RoomManager not initialized"));
                allHealthy = false;
            }
        } catch (Exception e) {
            healthStatus.addComponent("roomManager",
                new HealthStatus.ComponentHealth("unhealthy", "Error: " + e.getMessage()));
            allHealthy = false;
        }

        // Check MessageConsumer
        try {
            if (messageConsumer != null) {
                healthStatus.addComponent("messageConsumer",
                    new HealthStatus.ComponentHealth("healthy", "Consumer threads running"));
            } else {
                healthStatus.addComponent("messageConsumer",
                    new HealthStatus.ComponentHealth("unhealthy", "Consumer not initialized"));
                allHealthy = false;
            }
        } catch (Exception e) {
            healthStatus.addComponent("messageConsumer",
                new HealthStatus.ComponentHealth("unhealthy", "Error: " + e.getMessage()));
            allHealthy = false;
        }

        // Add JVM metrics
        Runtime runtime = Runtime.getRuntime();
        healthStatus.addMetric("memoryUsedMB", (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024));
        healthStatus.addMetric("memoryMaxMB", runtime.maxMemory() / (1024 * 1024));
        healthStatus.addMetric("availableProcessors", runtime.availableProcessors());

        // Set overall status
        healthStatus.setStatus(allHealthy ? "healthy" : "unhealthy");
        return healthStatus;
    }
}

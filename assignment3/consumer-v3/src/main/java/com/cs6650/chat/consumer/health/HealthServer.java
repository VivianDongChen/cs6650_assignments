package com.cs6650.chat.consumer.health;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.database.DatabaseConnectionPool;
import com.cs6650.chat.consumer.metrics.MetricsService;
import com.cs6650.chat.consumer.metrics.MetricsServlet;
import com.cs6650.chat.consumer.queue.MessageConsumer;
import com.cs6650.chat.consumer.websocket.BroadcastWebSocketHandler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

/**
 * Embedded Jetty server for health check endpoints, metrics API, and WebSocket broadcast endpoint.
 */
public class HealthServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthServer.class);
    private static final int HEALTH_PORT = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8080"));

    private final Server server;
    private final MessageConsumer messageConsumer;
    private final RoomManager roomManager;
    private final DatabaseConnectionPool connectionPool;

    public HealthServer(MessageConsumer messageConsumer, RoomManager roomManager, DatabaseConnectionPool connectionPool) {
        this.messageConsumer = messageConsumer;
        this.roomManager = roomManager;
        this.connectionPool = connectionPool;
        this.server = new Server(HEALTH_PORT);
        configureServer();
    }

    private void configureServer() {
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);

        // Add health check endpoint
        HealthCheckServlet healthServlet = new HealthCheckServlet(messageConsumer);
        context.addServlet(new ServletHolder(healthServlet), "/health");

        // Add simple status endpoint
        context.addServlet(new ServletHolder(new StatusServlet()), "/status");

        // Add metrics API endpoint
        MetricsService metricsService = new MetricsService(connectionPool);
        MetricsServlet metricsServlet = new MetricsServlet(metricsService);
        context.addServlet(new ServletHolder(metricsServlet), "/metrics");

        // Configure WebSocket endpoint for client broadcast connections
        JettyWebSocketServletContainerInitializer.configure(context, (servletContext, wsContainer) -> {
            // Set WebSocket timeout to 10 minutes
            wsContainer.setIdleTimeout(Duration.ofMinutes(10));
            wsContainer.setMaxTextMessageSize(65536);

            // Map WebSocket endpoint: /broadcast/{roomId}
            wsContainer.addMapping("/broadcast/*", (req, resp) -> new BroadcastWebSocketHandler(roomManager));

            LOGGER.info("WebSocket broadcast endpoint configured at /broadcast/*");
        });
    }

    /**
     * Start the health server.
     */
    public void start() throws Exception {
        try {
            server.start();
            LOGGER.info("Health check server started on port {}", HEALTH_PORT);
            LOGGER.info("Health endpoint: http://localhost:{}/health", HEALTH_PORT);
            LOGGER.info("Status endpoint: http://localhost:{}/status", HEALTH_PORT);
            LOGGER.info("Metrics endpoint: http://localhost:{}/metrics", HEALTH_PORT);
        } catch (Exception e) {
            LOGGER.error("Failed to start health server", e);
            throw e;
        }
    }

    /**
     * Stop the health server.
     */
    public void stop() throws Exception {
        try {
            server.stop();
            LOGGER.info("Health check server stopped");
        } catch (Exception e) {
            LOGGER.error("Error stopping health server", e);
            throw e;
        }
    }

    /**
     * Simple status servlet that always returns 200 OK.
     * Useful for basic connectivity checks.
     */
    private static class StatusServlet extends jakarta.servlet.http.HttpServlet {
        @Override
        protected void doGet(jakarta.servlet.http.HttpServletRequest req,
                             jakarta.servlet.http.HttpServletResponse resp) throws java.io.IOException {
            resp.setStatus(jakarta.servlet.http.HttpServletResponse.SC_OK);
            resp.setContentType("text/plain");
            resp.getWriter().write("OK");
        }
    }
}

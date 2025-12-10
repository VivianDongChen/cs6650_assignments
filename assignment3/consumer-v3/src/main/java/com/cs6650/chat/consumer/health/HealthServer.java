package com.cs6650.chat.consumer.health;

import com.cs6650.chat.consumer.broadcast.RoomManager;
import com.cs6650.chat.consumer.cache.CacheManager;
import com.cs6650.chat.consumer.cache.HotDataCache;
import com.cs6650.chat.consumer.cache.MetricsCacheDecorator;
import com.cs6650.chat.consumer.cache.RedisConnectionPool;
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
 * Integrates Redis caching layer for metrics queries via MetricsCacheDecorator.
 */
public class HealthServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthServer.class);
    private static final int HEALTH_PORT = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8080"));

    private final Server server;
    private final MessageConsumer messageConsumer;
    private final RoomManager roomManager;
    private final DatabaseConnectionPool connectionPool;
    private CacheManager cacheManager;

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

        // Initialize Redis connection pool and metrics caching
        try {
            RedisConnectionPool.initialize();  // Uses environment variables or defaults
            MetricsService metricsService = new MetricsService(connectionPool);
            MetricsCacheDecorator metricsCache = new MetricsCacheDecorator(
                metricsService,
                RedisConnectionPool.getInstance()
            );

            // Initialize Level 1 & 2: HotDataCache (in-memory Caffeine)
            HotDataCache hotDataCache = new HotDataCache();

            // Initialize cache manager for lifecycle management
            // Integrates all 3 cache layers: L1 (Caffeine), L2 (Redis), L3 (Invalidation)
            this.cacheManager = CacheManager.initialize(
                RedisConnectionPool.getInstance(),
                metricsCache,
                hotDataCache
            );

            // Start cache maintenance tasks (includes Level 3 invalidation strategy)
            this.cacheManager.startMaintenanceTasks();

            // Create wrapper servlet that uses 3-layer cache
            MetricsServlet metricsServlet = new MetricsServlet(metricsService, metricsCache);
            context.addServlet(new ServletHolder(metricsServlet), "/metrics");

            LOGGER.info("3-layer cache initialized: L1 (Caffeine), L2 (Redis), L3 (Invalidation)");
        } catch (Exception e) {
            LOGGER.warn("Cache initialization failed, falling back to direct database queries: {}", e.getMessage());
            // Fallback to direct database queries without caching
            MetricsService metricsService = new MetricsService(connectionPool);
            MetricsServlet metricsServlet = new MetricsServlet(metricsService);
            context.addServlet(new ServletHolder(metricsServlet), "/metrics");
        }

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
            // Gracefully shutdown cache manager (Level 1, 2, 3)
            if (this.cacheManager != null) {
                this.cacheManager.shutdown();
            }
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

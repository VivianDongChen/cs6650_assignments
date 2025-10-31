package com.cs6650.chat.consumer.health;

import com.cs6650.chat.consumer.queue.MessageConsumer;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Embedded Jetty server for health check endpoints.
 */
public class HealthServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(HealthServer.class);
    private static final int HEALTH_PORT = Integer.parseInt(System.getenv().getOrDefault("HEALTH_PORT", "8080"));

    private final Server server;
    private final MessageConsumer messageConsumer;

    public HealthServer(MessageConsumer messageConsumer) {
        this.messageConsumer = messageConsumer;
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

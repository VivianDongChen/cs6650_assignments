package com.cs6650.chat.consumer.metrics;

import com.cs6650.chat.consumer.cache.MetricsCacheDecorator;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Servlet for the /metrics endpoint.
 * Returns JSON metrics from the database or Redis cache if available.
 * Supports optional MetricsCacheDecorator for distributed caching.
 */
public class MetricsServlet extends HttpServlet {
    private static final Logger LOGGER = LoggerFactory.getLogger(MetricsServlet.class);
    private final MetricsService metricsService;
    private final MetricsCacheDecorator metricsCache;

    /**
     * Constructor with caching support.
     */
    public MetricsServlet(MetricsService metricsService, MetricsCacheDecorator metricsCache) {
        this.metricsService = metricsService;
        this.metricsCache = metricsCache;
    }

    /**
     * Constructor without caching (fallback).
     */
    public MetricsServlet(MetricsService metricsService) {
        this.metricsService = metricsService;
        this.metricsCache = null;
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            LOGGER.info("Metrics requested");

            // Refresh materialized views to get latest stats
            metricsService.refreshMaterializedViews();

            // Get metrics (with caching if available)
            String metricsJson;
            if (metricsCache != null) {
                metricsJson = metricsCache.getMetricsJson();
                LOGGER.debug("Metrics retrieved via Redis cache decorator");
            } else {
                metricsJson = metricsService.getMetricsJson();
                LOGGER.debug("Metrics retrieved directly from database");
            }

            // Send response
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.getWriter().write(metricsJson);

            LOGGER.info("Metrics returned successfully");
        } catch (Exception e) {
            LOGGER.error("Error handling metrics request", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Internal server error: " + e.getMessage() + "\"}");
        }
    }
}

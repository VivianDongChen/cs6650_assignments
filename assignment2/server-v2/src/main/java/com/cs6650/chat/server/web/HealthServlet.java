package com.cs6650.chat.server.web;

import com.cs6650.chat.server.config.ObjectMapperProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Simple health endpoint used for deployment verification and uptime monitoring.
 */
public class HealthServlet extends HttpServlet {

    private static final ObjectMapper OBJECT_MAPPER = ObjectMapperProvider.get();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");

        Map<String, Object> body = new HashMap<>();
        body.put("status", "UP");
        body.put("timestamp", Instant.now().toString());

        OBJECT_MAPPER.writeValue(resp.getWriter(), body);
    }
}

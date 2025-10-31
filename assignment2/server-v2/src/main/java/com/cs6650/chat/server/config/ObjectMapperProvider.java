package com.cs6650.chat.server.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Centralizes ObjectMapper configuration so all components share the same JSON settings.
 */
public final class ObjectMapperProvider {

    private static final ObjectMapper MAPPER = create();

    private ObjectMapperProvider() {
        // utility class
    }

    private static ObjectMapper create() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    public static ObjectMapper get() {
        return MAPPER;
    }
}

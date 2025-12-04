package com.cs6650.chat.server.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

/**
 * Enumerates the allowed chat message types defined in the assignment.
 */
public enum MessageType {
    TEXT,
    JOIN,
    LEAVE;

    /**
     * Case-insensitive factory used by Jackson during deserialization.
     * Returns {@code null} when the value cannot be mapped; validation handles the error later.
     */
    @JsonCreator
    public static MessageType fromValue(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            return MessageType.valueOf(raw.trim().toUpperCase(Locale.US));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /**
     * Ensures the enum is serialized as TEXT/JOIN/LEAVE when written to JSON.
     */
    @JsonValue
    public String toValue() {
        return name();
    }
}

package com.cs6650.chat.server.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * Represents the inbound chat message payload sent by WebSocket clients.
 * Each field maps directly to the JSON schema expected by the assignment.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChatMessage {

    // Optional client-generated identifier for end-to-end tracing.
    @JsonProperty("messageId")
    private String messageId;

    // Optional client-side send time (UTC millis) to compute end-to-end latency.
    @JsonProperty("clientSendTime")
    private Instant clientSendTime;

    // User identifier, must be between 1 and 100000 per the spec.
    @JsonProperty(value = "userId", required = true)
   private Integer userId;

    // Username, constrained to 3-20 alphanumeric characters.
    @JsonProperty(value = "username", required = true)
    private String username;

    // Chat message body, required and limited to 1-500 characters.
    @JsonProperty(value = "message", required = true)
    private String message;

    // Business timestamp; ISO-8601 strings will be deserialized into Instant.
    @JsonProperty(value = "timestamp", required = true)
    private Instant timestamp;

    // Message type (TEXT/JOIN/LEAVE) enforced via enumeration.
    @JsonProperty(value = "messageType", required = true)
    private MessageType messageType;

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public Instant getClientSendTime() {
        return clientSendTime;
    }

    public void setClientSendTime(Instant clientSendTime) {
        this.clientSendTime = clientSendTime;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public MessageType getMessageType() {
        return messageType;
    }

    public void setMessageType(MessageType messageType) {
        this.messageType = messageType;
    }
}

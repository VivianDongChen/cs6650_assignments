package com.cs6650.chat.client.message;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public final class ChatMessage {

    @JsonProperty("messageId")
    private final String messageId;

    @JsonProperty("clientSendTime")
    private final Instant clientSendTime;

    @JsonProperty("userId")
    private final int userId;

    @JsonProperty("username")
    private final String username;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("timestamp")
    private final Instant timestamp;

    @JsonProperty("messageType")
    private final String messageType;

    @JsonProperty("roomId")
    private final int roomId;

    public ChatMessage(String messageId,
                       Instant clientSendTime,
                       int userId,
                       String username,
                       String message,
                       Instant timestamp,
                       String messageType,
                       int roomId) {
        this.messageId = messageId;
        this.clientSendTime = clientSendTime;
        this.userId = userId;
        this.username = username;
        this.message = message;
        this.timestamp = timestamp;
        this.messageType = messageType;
        this.roomId = roomId;
    }

    public ChatMessage withClientSendTime(Instant sendTime) {
        return new ChatMessage(messageId, sendTime, userId, username, message, timestamp, messageType, roomId);
    }

    public String messageId() {
        return messageId;
    }

    public Instant clientSendTime() {
        return clientSendTime;
    }

    public int userId() {
        return userId;
    }

    public String username() {
        return username;
    }

    public String message() {
        return message;
    }

    public Instant timestamp() {
        return timestamp;
    }

    public String messageType() {
        return messageType;
    }

    public int roomId() {
        return roomId;
    }
}

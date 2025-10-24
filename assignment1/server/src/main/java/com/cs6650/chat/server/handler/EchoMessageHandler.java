package com.cs6650.chat.server.handler;

import com.cs6650.chat.server.model.ChatMessage;
import com.cs6650.chat.server.validation.MessageValidator.ValidationResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Builds the JSON responses returned to WebSocket clients.
 * Encapsulating this logic keeps the endpoint lean and easier to test.
 */
public class EchoMessageHandler {

    private final ObjectMapper objectMapper;

    /**
     * ObjectMapper is supplied externally so it can be configured consistently (e.g. JavaTimeModule).
     */
    public EchoMessageHandler(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * Construct a success payload that echoes the original message and adds server timestamps.
     *
     * @param message    validated inbound message
     * @param receivedAt timestamp recorded when the server received the message
     * @return ObjectNode ready to serialize back to the client
     */
    public ObjectNode buildSuccessResponse(ChatMessage message, Instant receivedAt) {
        Instant sendTime = Instant.now();
        ObjectNode response = objectMapper.createObjectNode();
        if (message.getMessageId() != null) {
            response.put("messageId", message.getMessageId());
        }
        response.put("status", "success");
        response.put("serverReceiveTime", receivedAt.toString());
        response.put("serverSendTime", sendTime.toString());
        response.set("originalMessage", objectMapper.valueToTree(message));
        return response;
    }

    /**
     * Construct an error payload that returns validation failures to the client.
     *
     * @param validationResult validation outcome returned from MessageValidator
     * @return ObjectNode describing the validation issues
     */
    public ObjectNode buildValidationErrorResponse(ValidationResult validationResult) {
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "error");
        ArrayNode errors = response.putArray("errors");
        List<String> errorMessages = validationResult.getErrors();
        errorMessages.forEach(errors::add);
        return response;
    }
}

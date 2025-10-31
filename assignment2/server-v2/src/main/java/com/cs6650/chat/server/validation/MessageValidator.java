package com.cs6650.chat.server.validation;

import com.cs6650.chat.server.model.ChatMessage;
import com.cs6650.chat.server.model.MessageType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates incoming {@link ChatMessage} instances against the assignment rules.
 * The validator is stateless and therefore safe to reuse across threads.
 */
public class MessageValidator {

    private static final int USER_ID_MIN = 1;
    private static final int USER_ID_MAX = 100_000;
    private static final int MESSAGE_MIN_LENGTH = 1;
    private static final int MESSAGE_MAX_LENGTH = 500;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9]{3,20}$");

    /**
     * Validate the given message and collect any violations.
     *
     * @param message message payload to validate
     * @return ValidationResult containing overall validity and error messages
     */
    public ValidationResult validate(ChatMessage message) {
        List<String> errors = new ArrayList<>();

        if (message == null) {
            errors.add("Message body is required.");
            return ValidationResult.invalid(errors);
        }

        validateUserId(message.getUserId(), errors);
        validateUsername(message.getUsername(), errors);
        validateMessage(message.getMessage(), errors);
        validateTimestamp(message.getTimestamp(), errors);
        validateMessageType(message.getMessageType(), errors);

        return errors.isEmpty() ? ValidationResult.valid()
                                : ValidationResult.invalid(errors);
    }

    // userId must be present and within the allowed numeric range.
    private void validateUserId(Integer userId, List<String> errors) {
        if (userId == null) {
            errors.add("userId is required.");
            return;
        }
        if (userId < USER_ID_MIN || userId > USER_ID_MAX) {
            errors.add("userId must be between 1 and 100000.");
        }
    }

    // username must be present and meet the 3-20 alphanumeric constraint.
    private void validateUsername(String username, List<String> errors) {
        if (username == null || username.isBlank()) {
            errors.add("username is required.");
            return;
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            errors.add("username must be 3-20 alphanumeric characters.");
        }
    }

    // message body must exist and be between 1 and 500 characters.
    private void validateMessage(String message, List<String> errors) {
        if (message == null) {
            errors.add("message is required.");
            return;
        }
        int length = message.length();
        if (length < MESSAGE_MIN_LENGTH || length > MESSAGE_MAX_LENGTH) {
            errors.add("message must be between 1 and 500 characters.");
        }
    }

    // timestamp must be present; Jackson parsing already enforces ISO-8601 format.
    private void validateTimestamp(Instant timestamp, List<String> errors) {
        if (timestamp == null) {
            errors.add("timestamp is required and must be ISO-8601 formatted.");
        }
    }

    // messageType must successfully resolve to a known enum value.
    private void validateMessageType(MessageType type, List<String> errors) {
        if (type == null) {
            errors.add("messageType must be one of TEXT, JOIN, LEAVE.");
        }
    }

    /**
     * Encapsulates the outcome of a validation pass, including failures when present.
     */
    public static final class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors == null ? List.of() : List.copyOf(errors);
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, Collections.emptyList());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }
    }
}

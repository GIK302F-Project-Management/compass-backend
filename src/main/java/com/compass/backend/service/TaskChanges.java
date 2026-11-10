package com.compass.backend.service;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.compass.backend.exception.InvalidTaskChangesException;
import com.compass.backend.model.Task;

/**
 * The fields a PATCH /tasks/{id} wants to change. A field that is missing from the body stays as it
 * is, which is different from a field that is null (clearing the deadline or the estimate), so the
 * body is read as a map instead of a fixed record. Other members' edits to other fields survive.
 */
public final class TaskChanges {

    private static final Set<String> FIELDS = Set.of("title", "details", "dueDate", "estimatedMinutes", "done");

    private final Map<String, Object> values = new LinkedHashMap<>();

    private TaskChanges() {
    }

    /**
     * @brief This method checks a request body and turns it into changes.
     * @param body the JSON object sent by the client.
     * @return the validated changes.
     * @throws InvalidTaskChangesException with one message per wrong field.
     */
    public static TaskChanges from(Map<String, Object> body) {
        Map<String, String> errors = new LinkedHashMap<>();
        TaskChanges changes = new TaskChanges();

        for (Map.Entry<String, Object> entry : body.entrySet()) {
            String field = entry.getKey();
            Object value = entry.getValue();
            if (!FIELDS.contains(field)) {
                errors.put(field, "This field cannot be changed.");
                continue;
            }
            try {
                changes.values.put(field, parse(field, value));
            } catch (IllegalArgumentException ex) {
                errors.put(field, ex.getMessage());
            }
        }
        if (changes.values.isEmpty() && errors.isEmpty()) {
            errors.put("body", "Send at least one field to change.");
        }
        if (!errors.isEmpty()) {
            throw new InvalidTaskChangesException(errors);
        }
        return changes;
    }

    private static Object parse(String field, Object value) {
        return switch (field) {
            case "title" -> {
                String title = text(value, "The title must be text.");
                if (title.isEmpty()) {
                    throw new IllegalArgumentException("Please give the task a title.");
                }
                if (title.length() > Task.MAX_TITLE_LENGTH) {
                    throw new IllegalArgumentException(
                        "The title must have at most " + Task.MAX_TITLE_LENGTH + " characters.");
                }
                yield title;
            }
            case "details" -> {
                String details = value == null ? "" : text(value, "The details must be text.");
                if (details.length() > Task.MAX_DETAILS_LENGTH) {
                    throw new IllegalArgumentException(
                        "The details must have at most " + Task.MAX_DETAILS_LENGTH + " characters.");
                }
                yield details;
            }
            case "dueDate" -> {
                if (value == null) {
                    yield null;
                }
                try {
                    yield LocalDate.parse(text(value, "The due date must be a date like 2026-11-30."));
                } catch (DateTimeParseException ex) {
                    throw new IllegalArgumentException("The due date must be a date like 2026-11-30.");
                }
            }
            case "estimatedMinutes" -> {
                if (value == null) {
                    yield null;
                }
                String message = "The estimate must be a whole number of minutes between 1 and "
                    + Task.MAX_ESTIMATED_MINUTES + ".";
                if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                        || number.doubleValue() < 1 || number.doubleValue() > Task.MAX_ESTIMATED_MINUTES) {
                    throw new IllegalArgumentException(message);
                }
                yield number.intValue();
            }
            default -> {
                if (!(value instanceof Boolean done)) {
                    throw new IllegalArgumentException("Done must be true or false.");
                }
                yield done;
            }
        };
    }

    private static String text(Object value, String message) {
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(message);
        }
        return text.trim();
    }

    /** Applies the changes to a task; {@code userId} is credited when the task gets completed. */
    void applyTo(Task task, int userId) {
        values.forEach((field, value) -> {
            switch (field) {
                case "title" -> task.setTitle((String) value);
                case "details" -> task.setDetails((String) value);
                case "dueDate" -> task.setDueDate((LocalDate) value);
                case "estimatedMinutes" -> task.setEstimatedMinutes((Integer) value);
                default -> {
                    boolean done = (Boolean) value;
                    // Completing a task that is already done keeps the first member's credit.
                    if (done != task.isDone()) {
                        task.setDone(done, userId);
                    }
                }
            }
        });
    }

}

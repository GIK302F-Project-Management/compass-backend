package com.compass.backend.dto;

import jakarta.validation.constraints.NotNull;

/** Body of PUT /activities/{id}/done: true to complete the activity, false to reopen it. */
public record DoneRequest(@NotNull(message = "Say whether it is done.") Boolean done) {
}

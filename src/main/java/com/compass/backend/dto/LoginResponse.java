package com.compass.backend.dto;

import java.time.Instant;

/**
 * Body of a successful login: the access token, when it stops working, and who it belongs to.
 * The client sends the token back as "Authorization: Bearer &lt;token&gt;".
 */
public record LoginResponse(String token, Instant expiresAt, UserResponse user) {
}

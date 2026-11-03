package com.compass.backend.service;

import java.time.Instant;

/** A signed access token and the moment it expires. */
public record AccessToken(String value, Instant expiresAt) {
}

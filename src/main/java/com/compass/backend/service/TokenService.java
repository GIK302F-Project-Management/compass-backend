package com.compass.backend.service;

import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import com.compass.backend.model.User;

/**
 * Issues the signed access tokens (JWT, HS256) returned by a successful login.
 * The token only carries the user id; everything else is read from the database when needed.
 */
@Service
public class TokenService {

    public static final String ISSUER = "compass-backend";

    private final JwtEncoder encoder;
    private final Duration timeToLive;

    public TokenService(JwtEncoder encoder, @Value("${compass.jwt.ttl}") Duration timeToLive) {
        this.encoder = encoder;
        this.timeToLive = timeToLive;
    }

    /**
     * @brief This method creates an access token for a user.
     * @param user the authenticated user.
     * @return the signed token and its expiration time.
     */
    public AccessToken issue(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(timeToLive);

        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(ISSUER)
            .subject(String.valueOf(user.getId()))
            .issuedAt(now)
            .expiresAt(expiresAt)
            .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, expiresAt);
    }

}

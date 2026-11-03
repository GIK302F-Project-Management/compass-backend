package com.compass.backend.dto;

/**
 * Body of POST /user/register. It is the same whether the account was created or the email already
 * had one, so the answer never reveals which emails are registered. No account data is returned.
 */
public record RegisterResponse(String message) {

    public static final String MESSAGE = "Registration received. You can now log in with your email and password.";

    public static RegisterResponse received() {
        return new RegisterResponse(MESSAGE);
    }

}

package com.compass.backend.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.compass.backend.exception.InvalidCredentialsException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;

/**
 * Business rules for accounts: registration, credential checking and the profile.
 * The controller only translates HTTP to and from these calls.
 */
@Service
public class UserService {

    private static final String UNKNOWN_USER_HASH = PasswordHasher.hash("no-such-user");

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * @brief This method creates a new account, storing only the BCrypt hash of the password.
     *        When the email already has an account nothing is changed and nothing is reported, so the
     *        caller cannot use registration to find out which emails are registered.
     * @param name user name.
     * @param email user email (already normalized).
     * @param rawPassword password typed by the user.
     */
    public void register(String name, String email, String rawPassword) {
        // hash first: a taken email must cost the same time as a free one
        String hash = PasswordHasher.hash(rawPassword);

        if (users.existsByEmailIgnoreCase(email)) {
            return;
        }

        try {
            users.save(new User(0, name, email, hash));
        } catch (DataIntegrityViolationException e) {
            // two simultaneous requests passed the check above; the unique column stopped the second one
        }
    }

    /**
     * @brief This method checks the email and password of a user.
     * @param email user email.
     * @param rawPassword password typed by the user.
     * @return the user when the credentials are valid.
     * @throws InvalidCredentialsException when the email is unknown or the password does not match.
     */
    public User authenticate(String email, String rawPassword) {
        User user = users.findByEmailIgnoreCase(email).orElse(null);

        if (user == null) {
            // spend the same time as a real check, so response time does not reveal which emails exist
            PasswordHasher.matches(rawPassword, UNKNOWN_USER_HASH);
            throw new InvalidCredentialsException();
        }

        if (!PasswordHasher.matches(rawPassword, user.getPassword())) {
            throw new InvalidCredentialsException();
        }

        return user;
    }

    /**
     * @brief This method finds the account a token was issued to.
     * @param id user id.
     * @return the user.
     * @throws UserNotFoundException when the account no longer exists.
     */
    public User findById(int id) {
        return users.findById(id).orElseThrow(UserNotFoundException::new);
    }

    /**
     * @brief This method changes the name shown on the student's profile and to the members of their shared activities.
     * @param id user id.
     * @param name the new name (already trimmed and validated).
     * @return the updated user.
     * @throws UserNotFoundException when the account no longer exists.
     */
    public User rename(int id, String name) {
        User user = findById(id);
        user.setName(name);
        return users.save(user);
    }

}

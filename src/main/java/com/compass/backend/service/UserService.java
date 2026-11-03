package com.compass.backend.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import com.compass.backend.exception.EmailAlreadyRegisteredException;
import com.compass.backend.exception.UserNotFoundException;
import com.compass.backend.exception.WrongPasswordException;
import com.compass.backend.model.User;
import com.compass.backend.model.UserRepository;

/**
 * Business rules for accounts: registration and credential checking.
 * The controller only translates HTTP to and from these calls.
 */
@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    /**
     * @brief This method creates a new account, storing only the BCrypt hash of the password.
     * @param name user name.
     * @param email user email (already normalized).
     * @param rawPassword password typed by the user.
     * @return the stored user.
     * @throws EmailAlreadyRegisteredException when the email is already in use.
     */
    public User register(String name, String email, String rawPassword) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        try {
            return users.save(new User(0, name, email, PasswordHasher.hash(rawPassword)));
        } catch (DataIntegrityViolationException e) {
            // two simultaneous requests passed the check above; the unique column stopped the second one
            throw new EmailAlreadyRegisteredException(e);
        }
    }

    /**
     * @brief This method checks the email and password of a user.
     * @param email user email.
     * @param rawPassword password typed by the user.
     * @return the user when the credentials are valid.
     * @throws UserNotFoundException when the email is not registered.
     * @throws WrongPasswordException when the password does not match.
     */
    public User authenticate(String email, String rawPassword) {
        User user = users.findByEmailIgnoreCase(email).orElseThrow(UserNotFoundException::new);

        if (!PasswordHasher.matches(rawPassword, user.getPassword())) {
            throw new WrongPasswordException();
        }

        return user;
    }

}

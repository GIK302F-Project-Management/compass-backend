package com.compass.backend.model;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for users. Spring generates the implementation and the SQL
 * from the method names, so there are no hand-written queries.
 */
public interface UserRepository extends JpaRepository<User, Integer> {

    /**
     * @brief This method finds a user by email, ignoring upper/lower case.
     * @param email user email.
     * @return the matching user, or empty when it does not exist.
     */
    Optional<User> findByEmailIgnoreCase(String email);

    /**
     * @brief This method checks whether an email is already registered, ignoring upper/lower case.
     * @param email user email.
     * @return true when a user with this email exists.
     */
    boolean existsByEmailIgnoreCase(String email);

}

package com.compass.backend.model;

import java.util.ArrayList;
import java.util.List;

import com.compass.backend.service.PasswordHasher;

/**
 * In-memory repository. Data is lost on every restart; replace with a database
 * repository once the database is chosen.
 */
public enum UserRepository {
    instance;

    private final List<User> list = new ArrayList<>();

    private UserRepository() {
        this.list.add(new User(1, "Ana", "ana@alumni.mondragon.edu", PasswordHasher.hash("ana123")));
        this.list.add(new User(2, "Bruno", "bruno@alumni.mondragon.edu", PasswordHasher.hash("bruno123")));
    }

    /**
     * @brief This method returns all stored users.
     * @return the full user list.
     */
    public List<User> getAll() {
        return list;
    }

    /**
     * @brief This method adds a new user to the list.
     * @param user user to be added.
     */
    public void add(User user) {
        this.list.add(user);
    }

    /**
     * @brief This method registers a new user if the email is not taken yet. The check and the
     *        insert happen together so two simultaneous requests cannot create the same email twice.
     * @param name user name.
     * @param email user email (already normalized).
     * @param passwordHash BCrypt hash of the user password.
     * @return the created user, or null when the email is already registered.
     */
    public synchronized User register(String name, String email, String passwordHash) {
        if (findByEmail(email) != null)
            return null;

        int nextId = this.list.stream().mapToInt(User::getId).max().orElse(0) + 1;
        User user = new User(nextId, name, email, passwordHash);
        this.list.add(user);
        return user;
    }

    /**
     * @brief This method removes a user by position.
     * @param position position in the list.
     */
    public void deleteByPosition(int position) {
        this.list.remove(position);
    }

    /**
     * @brief This method finds a user by id.
     * @param id user id.
     * @return the matching user, or null when it does not exist.
     */
    public User findById(int id) {
        int position = findPositionById(id);
        return position == -1 ? null : this.list.get(position);
    }

    /**
     * @brief This method finds a user by email.
     * @param email user email.
     * @return the matching user, or null when it does not exist.
     */
    public User findByEmail(String email) {
        for (User user : this.list) {
            if (user.getEmail().equalsIgnoreCase(email))
                return user;
        }
        return null;
    }

    /**
     * @brief This method finds the position of a user by id.
     * @param id user id.
     * @return user position, or -1 when it does not exist.
     */
    public int findPositionById(int id) {
        for (int i = 0; i < this.list.size(); i++) {
            if (this.list.get(i).getId() == id)
                return i;
        }
        return -1;
    }

    /**
     * @brief This method updates an existing user by list position.
     * @param position user position in list.
     * @param name new user name.
     * @param email new user email.
     * @param password new user password.
     */
    public void update(int position, String name, String email, String password) {
        User user = this.list.get(position);
        user.setName(name);
        user.setEmail(email);
        user.setPassword(password);
    }

}

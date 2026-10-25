package com.prgate.users.domain.model;

import com.prgate.users.domain.event.*;
import com.prgate.users.domain.exception.InvalidUserStateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public class User {
    private static final int MAX_FIRST_NAME = 100;
    private static final int MAX_LAST_NAME = 100;
    private static final int MAX_COGNITO_SUB = 128;
    private static final int MAX_CONTACT_EMAIL = 254;
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UUID id;
    private final String cognitoSub;
    private String firstName;
    private String lastName;
    private String contactEmail;
    private UserRole role;
    private UserStatus status;
    private final Instant createdAt;
    private Instant updatedAt;
    private final List<DomainEvent> pendingEvents = new ArrayList<>();

    private User(UUID id,
            String cognitoSub,
            String firstName,
            String lastName,
            String contactEmail,
            UserRole role,
            UserStatus status,
            Instant createdAt,
            Instant updatedAt) {
        if (id == null) {
            throw new InvalidUserStateException("id must not be null");
        }
        if (role == null) {
            throw new InvalidUserStateException("role must not be null");
        }
        if (status == null) {
            throw new InvalidUserStateException("status must not be null");
        }
        if (createdAt == null) {
            throw new InvalidUserStateException("createdAt must not be null");
        }
        if (updatedAt == null) {
            throw new InvalidUserStateException("updatedAt must not be null");
        }
        this.id = id;
        this.cognitoSub = requireNonEmptyTrimmed(cognitoSub, "cognitoSub", MAX_COGNITO_SUB);
        this.firstName = normalizeName(firstName, "firstName");
        this.lastName = normalizeName(lastName, "lastName");
        this.contactEmail = normalizeEmail(contactEmail);
        this.role = role;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        validateTimeCoherence();
    }

    public static User register(UUID id,
            String cognitoSub,
            String firstName,
            String lastName,
            String contactEmail,
            Instant now) {
        Instant time = requireNonNull(now, "now");
        User user = new User(id,
                cognitoSub,
                firstName,
                lastName,
                contactEmail,
                UserRole.STUDENT,
                UserStatus.ACTIVE,
                time,
                time);
        user.pendingEvents.add(new UserCreated(
                user.id,
                user.cognitoSub,
                user.firstName,
                user.lastName,
                user.contactEmail,
                user.role,
                user.status,
                time));
        return user;
    }

    public void updateProfile(String firstName, String lastName, String contactEmail, Instant now) {
        Instant time = requireNonNull(now, "now");
        requireTimeNotBefore(time, this.updatedAt);

        String newFirstName = normalizeName(firstName, "firstName");
        String newLastName = normalizeName(lastName, "lastName");
        String newContactEmail = normalizeEmail(contactEmail);

        boolean changed = !Objects.equals(this.firstName, newFirstName)
                || !Objects.equals(this.lastName, newLastName)
                || !Objects.equals(this.contactEmail, newContactEmail);

        if (!changed) {
            return;
        }

        this.firstName = newFirstName;
        this.lastName = newLastName;
        this.contactEmail = newContactEmail;
        this.updatedAt = time;
        this.pendingEvents.add(new UserUpdated(this.id, this.firstName, this.lastName, this.contactEmail, time));
    }

    public void changeRole(UserRole newRole, Instant now) {
        UserRole requested = requireNonNull(newRole, "newRole");
        Instant time = requireNonNull(now, "now");
        requireTimeNotBefore(time, this.updatedAt);

        if (this.role == requested) {
            return;
        }

        UserRole previous = this.role;
        this.role = requested;
        this.updatedAt = time;
        this.pendingEvents.add(new UserRoleChanged(this.id, previous, this.role, time));
    }

    public void changeStatus(UserStatus newStatus, Instant now) {
        UserStatus requested = requireNonNull(newStatus, "newStatus");
        Instant time = requireNonNull(now, "now");
        requireTimeNotBefore(time, this.updatedAt);

        if (this.status == requested) {
            return;
        }

        UserStatus previous = this.status;
        this.status = requested;
        this.updatedAt = time;
        this.pendingEvents.add(new UserStatusChanged(this.id, previous, this.status, time));
    }

    public List<DomainEvent> pullDomainEvents() {
        List<DomainEvent> events = new ArrayList<>(this.pendingEvents);
        this.pendingEvents.clear();
        return events;
    }

    public List<DomainEvent> peekDomainEvents() {
        return new ArrayList<>(this.pendingEvents);
    }

    public UUID getId() {
        return id;
    }

    public String getCognitoSub() {
        return cognitoSub;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public UserRole getRole() {
        return role;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void validateTimeCoherence() {
        if (this.updatedAt.isBefore(this.createdAt)) {
            throw new InvalidUserStateException("updatedAt must not be before createdAt");
        }
    }

    private void requireTimeNotBefore(Instant time, Instant previous) {
        if (time.isBefore(previous)) {
            throw new InvalidUserStateException("modification time must not be before previous updatedAt");
        }
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        return value;
    }

    private String requireNonEmptyTrimmed(String value, String field, int maxLength) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUserStateException(field + " must not be empty");
        }
        if (trimmed.length() > maxLength) {
            throw new InvalidUserStateException(field + " exceeds maximum length of " + maxLength);
        }
        return trimmed;
    }

    private String normalizeName(String value, String field) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new InvalidUserStateException(field + " must not be empty or whitespace");
        }
        if (trimmed.length() > MAX_FIRST_NAME && field.equals("firstName")) {
            throw new InvalidUserStateException(field + " exceeds maximum length of " + MAX_FIRST_NAME);
        }
        if (trimmed.length() > MAX_LAST_NAME && field.equals("lastName")) {
            throw new InvalidUserStateException(field + " exceeds maximum length of " + MAX_LAST_NAME);
        }
        return trimmed;
    }

    private String normalizeEmail(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > MAX_CONTACT_EMAIL) {
            throw new InvalidUserStateException("contactEmail exceeds maximum length of " + MAX_CONTACT_EMAIL);
        }
        if (!EMAIL_PATTERN.matcher(trimmed).matches()) {
            throw new InvalidUserStateException("contactEmail has invalid format");
        }
        return trimmed;
    }
}

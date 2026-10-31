package com.prgate.users.domain.services;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.User;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class UserRegistrationService {

    public User register(UUID id,
            String cognitoSub,
            String firstName,
            String lastName,
            String contactEmail,
            Instant now) {
        requireNonNull(now, "now");
        return User.register(id, cognitoSub, firstName, lastName, contactEmail, now);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        return value;
    }
}

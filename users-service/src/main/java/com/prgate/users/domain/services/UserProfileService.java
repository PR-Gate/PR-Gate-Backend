package com.prgate.users.domain.services;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.User;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserProfileService {

    public void updateProfile(User user,
            String firstName,
            String lastName,
            String contactEmail,
            Instant now) {
        requireNonNull(user, "user");
        requireNonNull(now, "now");
        user.updateProfile(firstName, lastName, contactEmail, now);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        return value;
    }
}

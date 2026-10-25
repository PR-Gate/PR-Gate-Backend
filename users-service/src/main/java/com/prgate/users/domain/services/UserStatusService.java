package com.prgate.users.domain.services;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.User;
import com.prgate.users.domain.model.UserStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class UserStatusService {

    public void changeStatus(User user, UserStatus newStatus, Instant now) {
        requireNonNull(user, "user");
        requireNonNull(now, "now");
        user.changeStatus(newStatus, now);
    }

    private static <T> T requireNonNull(T value, String field) {
        if (value == null) {
            throw new InvalidUserStateException(field + " must not be null");
        }
        return value;
    }
}

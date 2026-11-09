package com.prgate.projects.infrastructure.users;

import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.CognitoIdentityDirectory;
import com.prgate.projects.application.port.out.UserDirectory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Development-only user projection; it never calls or populates the Users service. */
@Component
@Profile("local")
public class LocalUserDirectory implements UserDirectory, CognitoIdentityDirectory {
    private final Map<UUID, UserAccount> users;

    public LocalUserDirectory(@Value("${prgate.local.users:}") String configuredUsers) {
        this.users = Arrays.stream(configuredUsers.split(";"))
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .map(LocalUserDirectory::parse)
                .collect(Collectors.toUnmodifiableMap(UserAccount::id, Function.identity()));
    }

    @Override
    public Optional<UserAccount> findById(UUID userId) {
        return Optional.ofNullable(users.get(userId));
    }

    @Override
    public Optional<UserAccount> findByCognitoSub(String cognitoSub) {
        throw new UserDirectoryException("Cognito identity mapping is unavailable in the local profile");
    }

    private static UserAccount parse(String value) {
        String[] fields = value.split(":", -1);
        if (fields.length != 3) {
            throw new IllegalArgumentException("Local users must use UUID:ROLE:STATUS entries separated by semicolons");
        }
        try {
            return new UserAccount(UUID.fromString(fields[0].strip()),
                    UserAccount.Role.valueOf(fields[1].strip().toUpperCase()),
                    UserAccount.Status.valueOf(fields[2].strip().toUpperCase()));
        } catch (RuntimeException failure) {
            throw new IllegalArgumentException("Invalid local user entry; expected UUID:STUDENT|TEACHER|ADMIN:ACTIVE|INACTIVE", failure);
        }
    }
}

package com.prgate.users.application.internal.commandservices;

import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.exception.UserNotFoundException;
import com.prgate.users.domain.model.User;
import com.prgate.users.domain.model.UserRole;
import com.prgate.users.domain.model.UserStatus;
import com.prgate.users.domain.services.UserProfileService;
import com.prgate.users.domain.services.UserRegistrationService;
import com.prgate.users.domain.services.UserRoleService;
import com.prgate.users.domain.services.UserStatusService;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Service
public class UserCommandService {
    private final UserRepository userRepository;
    private final UserRegistrationService userRegistrationService;
    private final UserProfileService userProfileService;
    private final UserRoleService userRoleService;
    private final UserStatusService userStatusService;

    public UserCommandService(UserRepository userRepository,
            UserRegistrationService userRegistrationService,
            UserProfileService userProfileService,
            UserRoleService userRoleService,
            UserStatusService userStatusService) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
        this.userRegistrationService = Objects.requireNonNull(userRegistrationService,
                "userRegistrationService must not be null");
        this.userProfileService = Objects.requireNonNull(userProfileService, "userProfileService must not be null");
        this.userRoleService = Objects.requireNonNull(userRoleService, "userRoleService must not be null");
        this.userStatusService = Objects.requireNonNull(userStatusService, "userStatusService must not be null");
    }

    public User registerUser(UUID id,
            String cognitoSub,
            String firstName,
            String lastName,
            String contactEmail,
            Instant now) {
        User user = userRegistrationService.register(id, cognitoSub, firstName, lastName, contactEmail, now);
        if (userRepository.existsByCognitoSub(user.getCognitoSub())) {
            throw new InvalidUserStateException("cognitoSub already registered");
        }
        return userRepository.save(user);
    }

    public User updateUserProfile(UUID userId,
            String firstName,
            String lastName,
            String contactEmail,
            Instant now) {
        User user = findUser(userId);
        userProfileService.updateProfile(user, firstName, lastName, contactEmail, now);
        return userRepository.save(user);
    }

    public User changeUserRole(UUID userId, UserRole newRole, Instant now) {
        User user = findUser(userId);
        userRoleService.changeRole(user, newRole, now);
        return userRepository.save(user);
    }

    public User changeUserStatus(UUID userId, UserStatus newStatus, Instant now) {
        User user = findUser(userId);
        userStatusService.changeStatus(user, newStatus, now);
        return userRepository.save(user);
    }

    private User findUser(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("user not found: " + userId));
    }
}

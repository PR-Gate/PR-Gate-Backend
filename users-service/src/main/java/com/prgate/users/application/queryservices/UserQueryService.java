package com.prgate.users.application.queryservices;

import com.prgate.users.domain.model.User;
import com.prgate.users.infraestructure.persistence.jpa.repositories.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@Service
public class UserQueryService {
    private final UserRepository userRepository;

    public UserQueryService(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository must not be null");
    }

    public Optional<User> getUserById(UUID userId) {
        Objects.requireNonNull(userId, "userId must not be null");
        return userRepository.findById(userId);
    }

    public Optional<User> getUserByCognitoSub(String cognitoSub) {
        Objects.requireNonNull(cognitoSub, "cognitoSub must not be null");
        return userRepository.findByCognitoSub(cognitoSub);
    }

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }
}

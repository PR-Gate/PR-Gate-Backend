package com.prgate.users.infraestructure.persistence.jpa.repositories;

import com.prgate.users.domain.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByCognitoSub(String cognitoSub);
    boolean existsByCognitoSub(String cognitoSub);
}

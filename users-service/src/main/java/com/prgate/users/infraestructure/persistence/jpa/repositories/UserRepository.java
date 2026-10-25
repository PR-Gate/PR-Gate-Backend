package com.prgate.users.infraestructure.persistence.jpa.repositories;

import com.prgate.users.domain.model.User;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class UserRepository {
    private final ConcurrentMap<UUID, User> usersById = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, UUID> idsByCognitoSub = new ConcurrentHashMap<>();

    public User save(User user) {
        Objects.requireNonNull(user, "user must not be null");
        usersById.put(user.getId(), user);
        idsByCognitoSub.put(user.getCognitoSub(), user.getId());
        return user;
    }

    public Optional<User> findById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return Optional.ofNullable(usersById.get(id));
    }

    public Optional<User> findByCognitoSub(String cognitoSub) {
        Objects.requireNonNull(cognitoSub, "cognitoSub must not be null");
        return Optional.ofNullable(idsByCognitoSub.get(cognitoSub)).map(usersById::get);
    }

    public List<User> findAll() {
        return new ArrayList<>(usersById.values());
    }

    public boolean existsById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        return usersById.containsKey(id);
    }

    public boolean existsByCognitoSub(String cognitoSub) {
        Objects.requireNonNull(cognitoSub, "cognitoSub must not be null");
        return idsByCognitoSub.containsKey(cognitoSub);
    }

    public void deleteById(UUID id) {
        Objects.requireNonNull(id, "id must not be null");
        User removed = usersById.remove(id);
        if (removed != null) {
            idsByCognitoSub.remove(removed.getCognitoSub(), id);
        }
    }

    public void delete(User user) {
        Objects.requireNonNull(user, "user must not be null");
        deleteById(user.getId());
    }
}

package com.prgate.users.interfaces.rest;

import com.prgate.users.application.internal.commandservices.UserCommandService;
import com.prgate.users.application.queryservices.UserQueryService;
import com.prgate.users.domain.model.User;
import com.prgate.users.interfaces.rest.resources.ChangeUserRoleResource;
import com.prgate.users.interfaces.rest.resources.ChangeUserStatusResource;
import com.prgate.users.interfaces.rest.resources.RegisterUserResource;
import com.prgate.users.interfaces.rest.resources.UpdateUserProfileResource;
import com.prgate.users.interfaces.rest.resources.UserResource;
import com.prgate.users.interfaces.rest.transform.UserResourceFromEntityAssembler;
import com.prgate.users.interfaces.rest.transform.UserRoleFromResourceAssembler;
import com.prgate.users.interfaces.rest.transform.UserStatusFromResourceAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/users")
@Tag(name = "Users", description = "Operaciones sobre usuarios")
public class UserController {
    private final UserCommandService userCommandService;
    private final UserQueryService userQueryService;

    public UserController(UserCommandService userCommandService, UserQueryService userQueryService) {
        this.userCommandService = Objects.requireNonNull(userCommandService,
                "userCommandService must not be null");
        this.userQueryService = Objects.requireNonNull(userQueryService, "userQueryService must not be null");
    }

    @PostMapping
    @Operation(summary = "Registrar un usuario")
    public ResponseEntity<UserResource> registerUser(@RequestBody RegisterUserResource resource) {
        User user = userCommandService.registerUser(
                UUID.randomUUID(),
                resource.cognitoSub(),
                resource.firstName(),
                resource.lastName(),
                resource.contactEmail(),
                Instant.now());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResourceFromEntityAssembler.toResourceFromEntity(user));
    }

    @GetMapping
    @Operation(summary = "Listar todos los usuarios")
    public ResponseEntity<List<UserResource>> getAllUsers() {
        List<UserResource> resources = UserResourceFromEntityAssembler
                .toResourcesFromEntities(userQueryService.getAllUsers());
        return ResponseEntity.ok(resources);
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Obtener un usuario por id")
    public ResponseEntity<UserResource> getUserById(@PathVariable UUID userId) {
        return userQueryService.getUserById(userId)
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/cognito/{cognitoSub}")
    @Operation(summary = "Obtener un usuario por cognitoSub")
    public ResponseEntity<UserResource> getUserByCognitoSub(@PathVariable String cognitoSub) {
        return userQueryService.getUserByCognitoSub(cognitoSub)
                .map(UserResourceFromEntityAssembler::toResourceFromEntity)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{userId}/profile")
    @Operation(summary = "Actualizar el perfil de un usuario")
    public ResponseEntity<UserResource> updateUserProfile(@PathVariable UUID userId,
            @RequestBody UpdateUserProfileResource resource) {
        User user = userCommandService.updateUserProfile(
                userId,
                resource.firstName(),
                resource.lastName(),
                resource.contactEmail(),
                Instant.now());
        return ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user));
    }

    @PatchMapping("/{userId}/role")
    @Operation(summary = "Cambiar el rol de un usuario")
    public ResponseEntity<UserResource> changeUserRole(@PathVariable UUID userId,
            @RequestBody ChangeUserRoleResource resource) {
        User user = userCommandService.changeUserRole(
                userId,
                UserRoleFromResourceAssembler.toRoleFromResource(resource),
                Instant.now());
        return ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user));
    }

    @PatchMapping("/{userId}/status")
    @Operation(summary = "Cambiar el estado de un usuario")
    public ResponseEntity<UserResource> changeUserStatus(@PathVariable UUID userId,
            @RequestBody ChangeUserStatusResource resource) {
        User user = userCommandService.changeUserStatus(
                userId,
                UserStatusFromResourceAssembler.toStatusFromResource(resource),
                Instant.now());
        return ResponseEntity.ok(UserResourceFromEntityAssembler.toResourceFromEntity(user));
    }
}

package com.prgate.users.interfaces.rest.internal;

import com.prgate.users.application.queryservices.UserQueryService;
import com.prgate.users.domain.exception.UserNotFoundException;
import com.prgate.users.domain.model.User;
import com.prgate.users.interfaces.rest.resources.InternalUserResource;
import com.prgate.users.interfaces.rest.transform.InternalUserResourceFromEntityAssembler;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/internal/v1/users")
@Tag(name = "Internal Users", description = "Consultas servicio a servicio usadas por Proyectos")
public class InternalUserController {
    private final UserQueryService userQueryService;

    public InternalUserController(UserQueryService userQueryService) {
        this.userQueryService = Objects.requireNonNull(userQueryService, "userQueryService must not be null");
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Consultar el rol y estado de un usuario",
            security = @SecurityRequirement(name = "serviceBearer"))
    public ResponseEntity<InternalUserResource> getUserById(@PathVariable("userId") UUID userId) {
        User user = userQueryService.getUserById(userId)
                .orElseThrow(() -> new UserNotFoundException("user not found: " + userId));
        return ResponseEntity.ok(InternalUserResourceFromEntityAssembler.toResourceFromEntity(user));
    }
}

package com.prgate.users.domain;

import com.prgate.users.domain.event.*;
import com.prgate.users.domain.exception.InvalidUserStateException;
import com.prgate.users.domain.model.User;
import com.prgate.users.domain.model.UserRole;
import com.prgate.users.domain.model.UserStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {
    private static final Instant T0 = Instant.parse("2025-01-01T00:00:00Z");
    private static final Instant T1 = Instant.parse("2025-01-01T01:00:00Z");
    private static final Instant T2 = Instant.parse("2025-01-01T02:00:00Z");

    @Test
    void register_createsUserWithStudentActiveAndEvent() {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        User user = User.register(id, "cognito-123", "John", "Doe", "john.doe@example.com", T0);

        assertEquals(id, user.getId());
        assertEquals("cognito-123", user.getCognitoSub());
        assertEquals("John", user.getFirstName());
        assertEquals("Doe", user.getLastName());
        assertEquals("john.doe@example.com", user.getContactEmail());
        assertEquals(UserRole.STUDENT, user.getRole());
        assertEquals(UserStatus.ACTIVE, user.getStatus());
        assertEquals(T0, user.getCreatedAt());
        assertEquals(T0, user.getUpdatedAt());

        List<DomainEvent> events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(UserCreated.class, events.get(0));
        UserCreated created = (UserCreated) events.get(0);
        assertEquals(id, created.userId());
        assertEquals("cognito-123", created.cognitoSub());
        assertEquals(UserRole.STUDENT, created.role());
        assertEquals(UserStatus.ACTIVE, created.status());
        assertEquals(T0, created.occurredOn());
    }

    @Test
    void register_trimsNames() {
        UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
        User user = User.register(id, "cognito-456", "  Alice  ", "  Smith  ", null, T0);
        assertEquals("Alice", user.getFirstName());
        assertEquals("Smith", user.getLastName());
        assertNull(user.getContactEmail());
    }

    @Test
    void rejectInvalidNames() {
        UUID id = UUID.randomUUID();
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", null, "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "", "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "   ", "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", null, null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", "", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", "   ", null, T0));
    }

    @Test
    void rejectInvalidCognitoSub() {
        UUID id = UUID.randomUUID();
        assertThrows(InvalidUserStateException.class, () -> User.register(id, null, "John", "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "", "John", "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "   ", "John", "Doe", null, T0));
        String longSub = "a".repeat(129);
        assertThrows(InvalidUserStateException.class, () -> User.register(id, longSub, "John", "Doe", null, T0));
    }

    @Test
    void validateEmail() {
        UUID id = UUID.randomUUID();
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", "Doe", "bad-email", T0));
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", "Doe", "a@b", T0));
        String longEmail = "a".repeat(250) + "@example.com";
        assertThrows(InvalidUserStateException.class, () -> User.register(id, "sub", "John", "Doe", longEmail, T0));
        User ok = User.register(id, "sub", "John", "Doe", "   ", T0);
        assertNull(ok.getContactEmail());
        User ok2 = User.register(id, "sub2", "John", "Doe", null, T0);
        assertNull(ok2.getContactEmail());
    }

    @Test
    void updateProfileUpdatesFieldsAndEvent() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", "john@example.com", T0);
        user.pullDomainEvents();

        user.updateProfile("Jane", "Roe", "jane.roe@example.com", T1);
        assertEquals("Jane", user.getFirstName());
        assertEquals("Roe", user.getLastName());
        assertEquals("jane.roe@example.com", user.getContactEmail());
        assertEquals(T1, user.getUpdatedAt());
        assertEquals(T0, user.getCreatedAt());

        List<DomainEvent> events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(UserUpdated.class, events.get(0));
        UserUpdated updated = (UserUpdated) events.get(0);
        assertEquals(id, updated.userId());
        assertEquals(T1, updated.occurredOn());
    }

    @Test
    void updateProfileAllowsNullEmail() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", "john@example.com", T0);
        user.pullDomainEvents();
        user.updateProfile("John", "Doe", null, T1);
        assertNull(user.getContactEmail());
        assertEquals(1, user.pullDomainEvents().size());
    }

    @Test
    void rejectModificationBeforePreviousUpdatedAt() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", null, T1);
        assertThrows(InvalidUserStateException.class, () -> user.updateProfile("Jane", "Doe", null, T0));
        assertThrows(InvalidUserStateException.class, () -> user.changeRole(UserRole.ADMIN, T0));
        assertThrows(InvalidUserStateException.class, () -> user.changeStatus(UserStatus.INACTIVE, T0));
    }

    @Test
    void changeRoleWorksAndNoEventIfSame() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", null, T0);
        user.pullDomainEvents();

        assertThrows(InvalidUserStateException.class, () -> user.changeRole(null, T1));

        user.changeRole(UserRole.TEACHER, T1);
        assertEquals(UserRole.TEACHER, user.getRole());
        assertEquals(T1, user.getUpdatedAt());
        List<DomainEvent> events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(UserRoleChanged.class, events.get(0));
        UserRoleChanged rc = (UserRoleChanged) events.get(0);
        assertEquals(UserRole.STUDENT, rc.previousRole());
        assertEquals(UserRole.TEACHER, rc.newRole());

        user.changeRole(UserRole.TEACHER, T2);
        assertEquals(0, user.pullDomainEvents().size());
        assertEquals(T1, user.getUpdatedAt());
    }

    @Test
    void changeStatusWorksAndNoEventIfSame() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", null, T0);
        user.pullDomainEvents();

        assertThrows(InvalidUserStateException.class, () -> user.changeStatus(null, T1));

        user.changeStatus(UserStatus.INACTIVE, T1);
        assertEquals(UserStatus.INACTIVE, user.getStatus());
        assertEquals(T1, user.getUpdatedAt());
        List<DomainEvent> events = user.pullDomainEvents();
        assertEquals(1, events.size());
        assertInstanceOf(UserStatusChanged.class, events.get(0));
        UserStatusChanged sc = (UserStatusChanged) events.get(0);
        assertEquals(UserStatus.ACTIVE, sc.previousStatus());
        assertEquals(UserStatus.INACTIVE, sc.newStatus());

        user.changeStatus(UserStatus.INACTIVE, T2);
        assertEquals(0, user.pullDomainEvents().size());
        assertEquals(T1, user.getUpdatedAt());
    }

    @Test
    void immutabilityOfIdCognitoSubCreatedAt() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub-immutable", "John", "Doe", null, T0);
        assertEquals(id, user.getId());
        assertEquals("sub-immutable", user.getCognitoSub());
        assertEquals(T0, user.getCreatedAt());
    }

    @Test
    void pendingEventsIsolationAndCleanup() {
        UUID id = UUID.randomUUID();
        User user = User.register(id, "sub", "John", "Doe", null, T0);
        List<DomainEvent> first = user.peekDomainEvents();
        assertEquals(1, first.size());
        first.clear(); // should not affect internal
        assertEquals(1, user.peekDomainEvents().size());

        List<DomainEvent> pulled = user.pullDomainEvents();
        assertEquals(1, pulled.size());
        pulled.clear();
        assertEquals(0, user.peekDomainEvents().size());
        assertEquals(0, user.pullDomainEvents().size());
    }
}

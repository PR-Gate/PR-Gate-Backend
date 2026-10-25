package com.prgate.projects.application.security;

import com.prgate.projects.application.exception.AccessDeniedException;
import com.prgate.projects.application.exception.UserDirectoryException;
import com.prgate.projects.application.exception.UserNotFoundException;
import com.prgate.projects.application.model.UserAccount;
import com.prgate.projects.application.port.out.UserDirectory;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;

import java.util.Locale;
import java.util.Objects;

/** Application policy backed by a port, independent of the Users transport. */
public final class UserAccessPolicy {

    private final UserDirectory users;

    public UserAccessPolicy(UserDirectory users) {
        this.users = Objects.requireNonNull(users, "User directory is required");
    }

    public TeacherId requireActiveTeacher(Actor actor) {
        return new TeacherId(requireActiveUser(actor, UserAccount.Role.TEACHER).id());
    }

    public StudentId requireActiveStudent(Actor actor) {
        return new StudentId(requireActiveUser(actor, UserAccount.Role.STUDENT).id());
    }

    private UserAccount requireActiveUser(Actor actor, UserAccount.Role expectedRole) {
        Objects.requireNonNull(actor, "Authenticated actor is required");
        UserAccount user = users.findById(actor.userId())
                .orElseThrow(() -> new UserNotFoundException("Authenticated user does not exist"));
        if (!user.id().equals(actor.userId())) {
            throw new UserDirectoryException("Users returned an identity different from the requested user");
        }
        if (user.status() != UserAccount.Status.ACTIVE || user.role() != expectedRole) {
            throw new AccessDeniedException("An active " + expectedRole.name().toLowerCase(Locale.ROOT) + " is required");
        }
        return user;
    }
}

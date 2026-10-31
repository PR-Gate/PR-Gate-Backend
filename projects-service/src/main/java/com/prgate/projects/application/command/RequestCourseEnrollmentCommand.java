package com.prgate.projects.application.command;

import java.util.UUID;

/** The applicant is always the authenticated actor, not an id from the request. */
public record RequestCourseEnrollmentCommand(UUID courseId) {
}

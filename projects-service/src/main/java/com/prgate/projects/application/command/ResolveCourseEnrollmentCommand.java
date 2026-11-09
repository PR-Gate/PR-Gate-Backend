package com.prgate.projects.application.command;

import com.prgate.projects.application.model.EnrollmentDecision;

import java.util.UUID;

public record ResolveCourseEnrollmentCommand(UUID enrollmentId, EnrollmentDecision decision) {
}

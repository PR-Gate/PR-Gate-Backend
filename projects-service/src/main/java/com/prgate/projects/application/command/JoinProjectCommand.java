package com.prgate.projects.application.command;

import java.util.UUID;

/** The student comes from Actor and the course comes from the stored project. */
public record JoinProjectCommand(UUID projectId) {
}

package com.prgate.projects.application.command;

import java.util.UUID;

public record CreateProjectCommand(UUID courseId, String name, String description) {
}

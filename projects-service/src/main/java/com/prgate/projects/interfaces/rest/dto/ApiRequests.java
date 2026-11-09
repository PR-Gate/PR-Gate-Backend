package com.prgate.projects.interfaces.rest.dto;

import com.prgate.projects.application.model.EnrollmentDecision;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class ApiRequests {
    private ApiRequests() { }
    public record CreateCourse(@NotBlank @Size(max=150) String name, @NotBlank @Size(max=50) String section,
                               @NotBlank @Size(max=30) String academicPeriod) { }
    public record CreateProject(@NotBlank @Size(max=150) String name, @Size(max=2000) String description) { }
    public record DecideEnrollment(@NotNull EnrollmentDecision decision) { }
    public record DesignateLeader(@NotNull UUID studentId) { }
    public record LinkOrganization(@NotBlank @Size(max=100) String organizationLogin) { }
    public record TransferMember(@NotNull UUID targetProjectId, @NotNull UUID studentId) { }
}

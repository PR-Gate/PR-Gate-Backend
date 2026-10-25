package com.prgate.projects.domain;

import com.prgate.projects.domain.model.aggregates.Project;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.GitHubOrganization;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

class ProjectTest {

    private static final ProjectId PROJECT_ID = new ProjectId(UUID.fromString("8bfc2272-a520-4995-852e-38ef0576def1"));
    private static final CourseId COURSE_ID = new CourseId(UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-24T15:00:00Z");
    private static final StudentId LEADER_ID = new StudentId(UUID.fromString("b4ed33f0-0987-4a1f-a988-5cd147ac861f"));
    private static final StudentId OTHER_STUDENT_ID = new StudentId(UUID.fromString("ccabff24-5281-438f-b1b5-6db91acf42d7"));

    @Test
    void createsProjectWithProvidedIdentityAndTime() {
        Project project = Project.create(PROJECT_ID, "PRGate", "Proyecto académico", COURSE_ID, CREATED_AT);

        assertThat(project.getId()).isEqualTo(PROJECT_ID);
        assertThat(project.getName()).isEqualTo("PRGate");
        assertThat(project.getDescription()).isEqualTo("Proyecto académico");
        assertThat(project.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(project.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(project.getGitHubOrganization()).isEmpty();
        assertThat(project.getLeaderId()).isEmpty();
    }

    @Test
    void linksAnOrganizationWithoutChangingProjectIdentityOrCourse() {
        Project project = Project.create(PROJECT_ID, "PRGate", "Proyecto académico", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        GitHubOrganization organization = new GitHubOrganization(123L, "prgate-team");

        project.linkGitHubOrganization(organization, LEADER_ID);

        assertThat(project.getGitHubOrganization()).contains(organization);
        assertThat(project.getId()).isEqualTo(PROJECT_ID);
        assertThat(project.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(project.getName()).isEqualTo("PRGate");
        assertThat(project.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void rejectsMissingOrganizationWithoutLinkingAnything() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> project.linkGitHubOrganization(null, LEADER_ID))
                .withMessage("GitHub organization is required");

        assertThat(project.getGitHubOrganization()).isEmpty();
    }

    @Test
    void cannotOverwriteLinkedOrganization() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        GitHubOrganization original = new GitHubOrganization(123L, "prgate-team");
        project.linkGitHubOrganization(original, LEADER_ID);

        assertThatIllegalStateException()
                .isThrownBy(() -> project.linkGitHubOrganization(new GitHubOrganization(456L, "other-team"), LEADER_ID))
                .withMessage("Project already has a linked GitHub organization");

        assertThat(project.getGitHubOrganization()).contains(original);
    }

    @Test
    void rejectsASecondLinkEvenForTheSameOrganization() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        GitHubOrganization organization = new GitHubOrganization(123L, "prgate-team");
        project.linkGitHubOrganization(organization, LEADER_ID);

        assertThatIllegalStateException().isThrownBy(() -> project.linkGitHubOrganization(organization, LEADER_ID));

        assertThat(project.getGitHubOrganization()).contains(organization);
    }

    @Test
    void removesSurroundingWhitespace() {
        Project project = Project.create(PROJECT_ID, "  PRGate  ", "\tProyecto académico\n", COURSE_ID, CREATED_AT);

        assertThat(project.getName()).isEqualTo("PRGate");
        assertThat(project.getDescription()).isEqualTo("Proyecto académico");
    }

    @Test
    void designatesAndReplacesASingleLeader() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);

        project.designateLeader(LEADER_ID);
        assertThat(project.getLeaderId()).contains(LEADER_ID);

        project.designateLeader(OTHER_STUDENT_ID);
        assertThat(project.getLeaderId()).contains(OTHER_STUDENT_ID);
    }

    @Test
    void repeatingDesignationKeepsTheSameLeader() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);

        project.designateLeader(LEADER_ID);

        assertThat(project.getLeaderId()).contains(LEADER_ID);
    }

    @Test
    void rejectsMissingLeaderWithoutOverwritingCurrentLeader() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> project.designateLeader(null))
                .withMessage("Leader id is required");

        assertThat(project.getLeaderId()).contains(LEADER_ID);
    }

    @Test
    void removesLeaderWithoutChangingProjectOrLinkedOrganization() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        GitHubOrganization organization = new GitHubOrganization(123L, "prgate-team");
        project.linkGitHubOrganization(organization, LEADER_ID);

        project.removeLeader();

        assertThat(project.getLeaderId()).isEmpty();
        assertThat(project.getGitHubOrganization()).contains(organization);
        assertThat(project.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(project.getId()).isEqualTo(PROJECT_ID);
    }

    @Test
    void removingLeaderFromUnassignedProjectHasNoEffect() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);

        project.removeLeader();

        assertThat(project.getLeaderId()).isEmpty();
    }

    @Test
    void cannotLinkOrganizationBeforeLeaderDesignation() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);

        assertThatIllegalStateException()
                .isThrownBy(() -> project.linkGitHubOrganization(new GitHubOrganization(123L, "prgate-team"), LEADER_ID))
                .withMessage("Only the project leader can link a GitHub organization");

        assertThat(project.getGitHubOrganization()).isEmpty();
    }

    @Test
    void anotherStudentCannotLinkOrganization() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);

        assertThatIllegalStateException()
                .isThrownBy(() -> project.linkGitHubOrganization(new GitHubOrganization(123L, "prgate-team"), OTHER_STUDENT_ID))
                .withMessage("Only the project leader can link a GitHub organization");

        assertThat(project.getGitHubOrganization()).isEmpty();
    }

    @Test
    void rejectsMissingActorWithoutLinkingOrganization() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);

        assertThatIllegalArgumentException()
                .isThrownBy(() -> project.linkGitHubOrganization(new GitHubOrganization(123L, "prgate-team"), null))
                .withMessage("Actor id is required");

        assertThat(project.getGitHubOrganization()).isEmpty();
    }

    @Test
    void formerLeaderCannotLinkOrganizationAfterReplacement() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        project.designateLeader(OTHER_STUDENT_ID);
        GitHubOrganization organization = new GitHubOrganization(123L, "prgate-team");

        assertThatIllegalStateException().isThrownBy(() -> project.linkGitHubOrganization(organization, LEADER_ID));
        project.linkGitHubOrganization(organization, OTHER_STUDENT_ID);

        assertThat(project.getLeaderId()).contains(OTHER_STUDENT_ID);
        assertThat(project.getGitHubOrganization()).contains(organization);
    }

    @Test
    void formerLeaderCannotLinkOrganizationAfterRemoval() {
        Project project = Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, CREATED_AT);
        project.designateLeader(LEADER_ID);
        project.removeLeader();

        assertThatIllegalStateException()
                .isThrownBy(() -> project.linkGitHubOrganization(new GitHubOrganization(123L, "prgate-team"), LEADER_ID));

        assertThat(project.getGitHubOrganization()).isEmpty();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n", "\u2003"})
    void rejectsMissingOrBlankName(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(PROJECT_ID, name, "", COURSE_ID, CREATED_AT))
                .withMessage("Project name is required");
    }

    @Test
    void acceptsNameAtLengthLimitAfterNormalization() {
        Project project = Project.create(PROJECT_ID, " " + "a".repeat(150) + " ", "", COURSE_ID, CREATED_AT);

        assertThat(project.getName()).hasSize(150);
    }

    @Test
    void rejectsNameAboveLengthLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(PROJECT_ID, "a".repeat(151), "", COURSE_ID, CREATED_AT))
                .withMessage("Project name must not exceed 150 characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void treatsMissingOrBlankDescriptionAsEmpty(String description) {
        Project project = Project.create(PROJECT_ID, "PRGate", description, COURSE_ID, CREATED_AT);

        assertThat(project.getDescription()).isEmpty();
    }

    @Test
    void acceptsDescriptionAtLengthLimitAfterNormalization() {
        Project project = Project.create(PROJECT_ID, "PRGate", " " + "a".repeat(2000) + " ", COURSE_ID, CREATED_AT);

        assertThat(project.getDescription()).hasSize(2000);
    }

    @Test
    void rejectsDescriptionAboveLengthLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(PROJECT_ID, "PRGate", "a".repeat(2001), COURSE_ID, CREATED_AT))
                .withMessage("Project description must not exceed 2000 characters");
    }

    @Test
    void rejectsMissingProjectId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(null, "PRGate", "", COURSE_ID, CREATED_AT))
                .withMessage("Project id is required");
    }

    @Test
    void rejectsMissingCourseId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(PROJECT_ID, "PRGate", "", null, CREATED_AT))
                .withMessage("Course id is required");
    }

    @Test
    void rejectsMissingCreationTime() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> Project.create(PROJECT_ID, "PRGate", "", COURSE_ID, null))
                .withMessage("Creation time is required");
    }
}

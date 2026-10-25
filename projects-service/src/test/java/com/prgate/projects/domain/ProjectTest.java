package com.prgate.projects.domain;

import com.prgate.projects.domain.model.Project;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProjectTest {

    private static final ProjectId PROJECT_ID = new ProjectId(UUID.fromString("8bfc2272-a520-4995-852e-38ef0576def1"));
    private static final CourseId COURSE_ID = new CourseId(UUID.fromString("f66317a1-d467-4736-bc2e-e01f9499bc3f"));
    private static final Instant CREATED_AT = Instant.parse("2026-10-24T15:00:00Z");

    @Test
    void createsProjectWithProvidedIdentityAndTime() {
        Project project = Project.create(PROJECT_ID, "PRGate", "Proyecto académico", COURSE_ID, CREATED_AT);

        assertThat(project.getId()).isEqualTo(PROJECT_ID);
        assertThat(project.getName()).isEqualTo("PRGate");
        assertThat(project.getDescription()).isEqualTo("Proyecto académico");
        assertThat(project.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(project.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void removesSurroundingWhitespace() {
        Project project = Project.create(PROJECT_ID, "  PRGate  ", "\tProyecto académico\n", COURSE_ID, CREATED_AT);

        assertThat(project.getName()).isEqualTo("PRGate");
        assertThat(project.getDescription()).isEqualTo("Proyecto académico");
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

package com.prgate.projects.domain;

import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.domain.model.valueobject.CourseEnrollmentId;
import com.prgate.projects.domain.model.valueobject.ProjectId;
import com.prgate.projects.domain.model.valueobject.ProjectMembershipId;
import com.prgate.projects.domain.model.valueobject.StudentId;
import com.prgate.projects.domain.model.valueobject.TeacherId;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class IdentifierTest {

    @Test
    void comparesIdentifiersByValueWithinTheirType() {
        UUID value = UUID.fromString("ee79db05-5926-4be1-9117-d6d1c05f59aa");

        assertThat(new CourseId(value)).isEqualTo(new CourseId(value));
        assertThat(new ProjectId(value)).isEqualTo(new ProjectId(value));
        assertThat(new TeacherId(value)).isEqualTo(new TeacherId(value));
        assertThat(new StudentId(value)).isEqualTo(new StudentId(value));
        assertThat(new CourseEnrollmentId(value)).isEqualTo(new CourseEnrollmentId(value));
        assertThat(new ProjectMembershipId(value)).isEqualTo(new ProjectMembershipId(value));
        assertThat(new CourseId(value)).isNotEqualTo(new ProjectId(value));
        assertThat(new CourseId(value)).isNotEqualTo(new TeacherId(value));
        assertThat(new StudentId(value)).isNotEqualTo(new TeacherId(value));
        assertThat(new CourseEnrollmentId(value)).isNotEqualTo(new CourseId(value));
        assertThat(new ProjectMembershipId(value)).isNotEqualTo(new CourseEnrollmentId(value));
    }

    @Test
    void rejectsMissingIdentifierValues() {
        assertThatIllegalArgumentException().isThrownBy(() -> new CourseId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProjectId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new TeacherId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new StudentId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new CourseEnrollmentId(null));
        assertThatIllegalArgumentException().isThrownBy(() -> new ProjectMembershipId(null));
    }
}

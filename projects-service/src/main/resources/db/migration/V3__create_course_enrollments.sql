CREATE TABLE course_enrollments (
    id UUID PRIMARY KEY,
    course_id UUID NOT NULL,
    student_id UUID NOT NULL,
    status VARCHAR(20) NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL,
    resolved_at TIMESTAMPTZ,
    CONSTRAINT fk_course_enrollments_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_course_enrollments_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT ck_course_enrollments_resolution CHECK (
        (status = 'PENDING' AND resolved_at IS NULL)
        OR (
            status IN ('APPROVED', 'REJECTED')
            AND resolved_at IS NOT NULL
            AND resolved_at >= requested_at
        )
    )
);

CREATE UNIQUE INDEX uq_course_enrollments_open
    ON course_enrollments (course_id, student_id)
    WHERE status IN ('PENDING', 'APPROVED');

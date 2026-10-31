CREATE TABLE courses (
    id UUID PRIMARY KEY,
    name VARCHAR(150) NOT NULL,
    section VARCHAR(50) NOT NULL,
    academic_period VARCHAR(30) NOT NULL,
    teacher_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_courses_name CHECK (btrim(name) <> ''),
    CONSTRAINT ck_courses_section CHECK (btrim(section) <> ''),
    CONSTRAINT ck_courses_academic_period CHECK (btrim(academic_period) <> '')
);

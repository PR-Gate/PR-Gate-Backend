package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.domain.model.valueobject.CourseId;
import com.prgate.projects.infrastructure.persistence.mapper.CoursePersistenceMapper;
import com.prgate.projects.infrastructure.persistence.repository.SpringDataCourseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public class JpaCourseRepositoryAdapter implements CourseRepository {

    private final SpringDataCourseRepository repository;

    public JpaCourseRepositoryAdapter(SpringDataCourseRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public void save(Course course) {
        repository.saveAndFlush(CoursePersistenceMapper.toEntity(course));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Course> findById(CourseId courseId) {
        return repository.findById(courseId.value()).map(CoursePersistenceMapper::toDomain);
    }
}

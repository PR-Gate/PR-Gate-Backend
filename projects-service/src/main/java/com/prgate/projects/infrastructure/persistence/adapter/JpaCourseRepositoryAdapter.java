package com.prgate.projects.infrastructure.persistence.adapter;

import com.prgate.projects.application.port.out.CourseRepository;
import com.prgate.projects.domain.model.aggregates.Course;
import com.prgate.projects.infrastructure.persistence.mapper.CoursePersistenceMapper;
import com.prgate.projects.infrastructure.persistence.repository.SpringDataCourseRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
}

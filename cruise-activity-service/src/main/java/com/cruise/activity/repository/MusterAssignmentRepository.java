package com.cruise.activity.repository;
import com.cruise.activity.entity.MusterAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MusterAssignmentRepository extends JpaRepository<MusterAssignment,Long> { java.util.List<MusterAssignment> findByPassengerIdIn(java.util.Collection<Long> ids); java.util.Optional<MusterAssignment> findByPassengerId(Long id); }

package com.cruise.activity.repository;

import com.cruise.activity.entity.ActivityRegistrationAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActivityRegistrationAuditLogRepository extends JpaRepository<ActivityRegistrationAuditLog, Long> {

    List<ActivityRegistrationAuditLog> findByActivityIdOrderByAuditTimeDesc(Long activityId);

    List<ActivityRegistrationAuditLog> findByRegistrationIdOrderByAuditTimeDesc(Long registrationId);
}

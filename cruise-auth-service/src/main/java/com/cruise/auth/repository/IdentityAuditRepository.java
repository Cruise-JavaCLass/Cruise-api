package com.cruise.auth.repository;

import com.cruise.auth.entity.IdentityAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdentityAuditRepository extends JpaRepository<IdentityAuditEvent, Long> {}

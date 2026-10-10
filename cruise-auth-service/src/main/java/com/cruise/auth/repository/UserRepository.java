package com.cruise.auth.repository;

import com.cruise.auth.entity.User;
import com.cruise.common.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA Repository quản lý bảng người dùng
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    Optional<User> findByRfidTag(String rfidTag);
    List<User> findByRole(Role role);
    boolean existsByUsername(String username);
}

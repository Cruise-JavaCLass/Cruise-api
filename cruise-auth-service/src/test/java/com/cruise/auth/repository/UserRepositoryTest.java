package com.cruise.auth.repository;

import com.cruise.auth.entity.User;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@DisplayName("UserRepository DataJpaTest Suite")
class UserRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .username("minh.nguyen")
                .fullName("Nguyễn Văn Minh")
                .email("minh.nguyen@cruise.com")
                .role(Role.PASSENGER)
                .cabinNumber("Cabin 402")
                .rfidTag("RFID-99412")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should persist user and auto-generate ID and audit timestamps")
    void shouldPersistUserWithAuditTimestamps() {
        User savedUser = userRepository.save(sampleUser);
        entityManager.flush();

        assertThat(savedUser.getId()).isNotNull();
        assertThat(savedUser.getCreatedAt()).isNotNull();
        assertThat(savedUser.getUpdatedAt()).isNotNull();
        assertThat(savedUser.getUsername()).isEqualTo("minh.nguyen");
    }

    @Test
    @DisplayName("Should find user by username")
    void shouldFindUserByUsername() {
        entityManager.persistAndFlush(sampleUser);

        Optional<User> found = userRepository.findByUsername("minh.nguyen");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Nguyễn Văn Minh");
        assertThat(found.get().getRole()).isEqualTo(Role.PASSENGER);
    }

    @Test
    @DisplayName("Should find user by RFID tag")
    void shouldFindUserByRfidTag() {
        entityManager.persistAndFlush(sampleUser);

        Optional<User> found = userRepository.findByRfidTag("RFID-99412");

        assertThat(found).isPresent();
        assertThat(found.get().getCabinNumber()).isEqualTo("Cabin 402");
    }

    @Test
    @DisplayName("Should find all users by role")
    void shouldFindUsersByRole() {
        entityManager.persist(sampleUser);

        User adminUser = User.builder()
                .username("admin.huy")
                .fullName("Huy Trần Gia")
                .email("admin@cruise.com")
                .role(Role.SYSTEM_ADMIN)
                .active(true)
                .build();
        entityManager.persist(adminUser);
        entityManager.flush();

        List<User> passengers = userRepository.findByRole(Role.PASSENGER);
        List<User> admins = userRepository.findByRole(Role.SYSTEM_ADMIN);

        assertThat(passengers).hasSize(1);
        assertThat(passengers.get(0).getUsername()).isEqualTo("minh.nguyen");
        assertThat(admins).hasSize(1);
        assertThat(admins.get(0).getUsername()).isEqualTo("admin.huy");
    }

    @Test
    @DisplayName("Should verify username existence")
    void shouldVerifyUsernameExistence() {
        entityManager.persistAndFlush(sampleUser);

        assertThat(userRepository.existsByUsername("minh.nguyen")).isTrue();
        assertThat(userRepository.existsByUsername("non.existent")).isFalse();
    }
}

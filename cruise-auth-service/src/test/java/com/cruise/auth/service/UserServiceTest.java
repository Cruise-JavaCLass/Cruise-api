package com.cruise.auth.service;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService Unit Tests with Mockito")
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .username("thao.tran")
                .fullName("Trần Thị Thu Thảo")
                .email("thao.tran@cruise.com")
                .role(Role.PASSENGER)
                .cabinNumber("Cabin 718")
                .rfidTag("RFID-99413")
                .active(true)
                .build();
    }

    @Test
    @DisplayName("Should successfully register new user when username is unique")
    void shouldRegisterUserSuccessfully() {
        when(userRepository.existsByUsername("thao.tran")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User registered = userService.registerUser(sampleUser);

        assertThat(registered).isNotNull();
        assertThat(registered.getUsername()).isEqualTo("thao.tran");
        assertThat(registered.getFullName()).isEqualTo("Trần Thị Thu Thảo");

        verify(userRepository, times(1)).existsByUsername("thao.tran");
        verify(userRepository, times(1)).save(sampleUser);
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when registering duplicate username")
    void shouldThrowExceptionWhenDuplicateUsername() {
        when(userRepository.existsByUsername("thao.tran")).thenReturn(true);

        assertThatThrownBy(() -> userService.registerUser(sampleUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Username already exists: thao.tran");

        verify(userRepository, times(1)).existsByUsername("thao.tran");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should find user by username when user exists")
    void shouldFindUserByUsername() {
        when(userRepository.findByUsername("thao.tran")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserByUsername("thao.tran");

        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("thao.tran@cruise.com");
        verify(userRepository, times(1)).findByUsername("thao.tran");
    }

    @Test
    @DisplayName("Should find user by RFID tag")
    void shouldFindUserByRfid() {
        when(userRepository.findByRfidTag("RFID-99413")).thenReturn(Optional.of(sampleUser));

        Optional<User> result = userService.getUserByRfid("RFID-99413");

        assertThat(result).isPresent();
        assertThat(result.get().getCabinNumber()).isEqualTo("Cabin 718");
        verify(userRepository, times(1)).findByRfidTag("RFID-99413");
    }

    @Test
    @DisplayName("Should retrieve users filtered by role")
    void shouldGetUsersByRole() {
        when(userRepository.findByRole(Role.PASSENGER)).thenReturn(List.of(sampleUser));

        List<User> passengers = userService.getUsersByRole(Role.PASSENGER);

        assertThat(passengers).hasSize(1);
        assertThat(passengers.get(0).getRole()).isEqualTo(Role.PASSENGER);
        verify(userRepository, times(1)).findByRole(Role.PASSENGER);
    }
}

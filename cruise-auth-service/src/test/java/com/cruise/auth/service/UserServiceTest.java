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
import java.util.NoSuchElementException;
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

    @Mock
    private JwtService jwtService;

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

    @Test
    @DisplayName("Should instantiate UserService with single repository argument")
    void shouldConstructWithSingleRepoArg() {
        UserService service = new UserService(userRepository);
        assertThat(service).isNotNull();
    }

    @Test
    @DisplayName("Should not re-hash password if it already starts with $2a$")
    void shouldNotRehashBCryptPassword() {
        sampleUser.setPassword("$2a$10$alreadyHashedBCryptValue123456789012345678901234");
        when(userRepository.existsByUsername("thao.tran")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User registered = userService.registerUser(sampleUser);

        assertThat(registered.getPassword()).isEqualTo("$2a$10$alreadyHashedBCryptValue123456789012345678901234");
    }

    @Test
    @DisplayName("Should handle registerUser when password is null or blank")
    void shouldHandleNullOrBlankPasswordOnRegister() {
        sampleUser.setPassword(null);
        when(userRepository.existsByUsername("thao.tran")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User registered1 = userService.registerUser(sampleUser);
        assertThat(registered1.getPassword()).isNull();

        sampleUser.setPassword("   ");
        User registered2 = userService.registerUser(sampleUser);
        assertThat(registered2.getPassword()).isEqualTo("   ");
    }

    @Test
    @DisplayName("Should delete user by username and revoke tokens when user exists")
    void shouldDeleteUserByUsernameWhenUserExists() {
        when(userRepository.findByUsername("thao.tran")).thenReturn(Optional.of(sampleUser));

        userService.deleteUserByUsername("thao.tran");

        verify(userRepository, times(1)).findByUsername("thao.tran");
        verify(userRepository, times(1)).delete(sampleUser);
        verify(jwtService, times(1)).revokeAllUserTokens("thao.tran");
    }

    @Test
    @DisplayName("Should do nothing when deleteUserByUsername for non-existent user")
    void shouldDoNothingWhenDeleteUserNonExistent() {
        when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

        userService.deleteUserByUsername("nonexistent");

        verify(userRepository, times(1)).findByUsername("nonexistent");
        verify(userRepository, never()).delete(any());
        verify(jwtService, never()).revokeAllUserTokens(any());
    }

    @Test
    @DisplayName("Should delete user without error when jwtService is null")
    void shouldDeleteUserWhenJwtServiceIsNull() {
        UserService serviceWithoutJwt = new UserService(userRepository);
        when(userRepository.findByUsername("thao.tran")).thenReturn(Optional.of(sampleUser));

        serviceWithoutJwt.deleteUserByUsername("thao.tran");

        verify(userRepository, times(1)).delete(sampleUser);
    }

    @Test
    @DisplayName("Should return all users")
    void shouldReturnAllUsers() {
        when(userRepository.findAll()).thenReturn(List.of(sampleUser));

        List<User> users = userService.getAllUsers();

        assertThat(users).containsExactly(sampleUser);
    }

    @Test
    @DisplayName("Should find user by id")
    void shouldFindUserById() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThat(userService.getUserById(1L)).contains(sampleUser);
        assertThat(userService.getUserById(2L)).isEmpty();
    }

    @Test
    @DisplayName("Should update full name, email and role of an existing user")
    void shouldUpdateUser() {
        sampleUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByEmail("new.mail@cruise.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateUser(1L, "  Trần Thảo  ", " new.mail@cruise.com ", Role.POS_STAFF);

        assertThat(updated.getFullName()).isEqualTo("Trần Thảo");
        assertThat(updated.getEmail()).isEqualTo("new.mail@cruise.com");
        assertThat(updated.getRole()).isEqualTo(Role.POS_STAFF);
        assertThat(updated.getUsername()).isEqualTo("thao.tran");
        verify(userRepository).save(sampleUser);
    }

    @Test
    @DisplayName("Should allow keeping the same email on update")
    void shouldAllowSameEmailOnUpdate() {
        sampleUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByEmail("thao.tran@cruise.com")).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User updated = userService.updateUser(1L, "Trần Thảo", "thao.tran@cruise.com", Role.PASSENGER);

        assertThat(updated.getEmail()).isEqualTo("thao.tran@cruise.com");
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when updating a missing user")
    void shouldThrowWhenUpdatingMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.updateUser(99L, "A", "a@cruise.com", Role.PASSENGER))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("User not found: 99");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject an email that belongs to another user")
    void shouldRejectEmailUsedByAnotherUser() {
        sampleUser.setId(1L);
        User other = User.builder().username("other").fullName("Other").email("taken@cruise.com")
                .role(Role.PASSENGER).build();
        other.setId(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.findByEmail("taken@cruise.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> userService.updateUser(1L, "Trần Thảo", "taken@cruise.com", Role.PASSENGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email already exists: taken@cruise.com");
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("Should reject blank full name, blank email and null role on update")
    void shouldRejectInvalidUpdateInput() {
        sampleUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));

        assertThatThrownBy(() -> userService.updateUser(1L, " ", "a@cruise.com", Role.PASSENGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Full name");
        assertThatThrownBy(() -> userService.updateUser(1L, "A", " ", Role.PASSENGER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Email");
        assertThatThrownBy(() -> userService.updateUser(1L, "A", "a@cruise.com", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Role");
    }

    @Test
    @DisplayName("Should deactivate and reactivate a user")
    void shouldToggleActiveStatus() {
        sampleUser.setId(1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(userService.setUserActive(1L, false).getActive()).isFalse();
        assertThat(userService.setUserActive(1L, true).getActive()).isTrue();
    }

    @Test
    @DisplayName("Should throw NoSuchElementException when toggling a missing user")
    void shouldThrowWhenTogglingMissingUser() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.setUserActive(99L, false))
                .isInstanceOf(NoSuchElementException.class);
    }
}
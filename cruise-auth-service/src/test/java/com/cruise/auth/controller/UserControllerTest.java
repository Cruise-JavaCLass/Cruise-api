package com.cruise.auth.controller;

import com.cruise.auth.entity.User;
import com.cruise.auth.service.UserService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@DisplayName("UserController MockMvc Web Layer Tests")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private com.cruise.auth.service.JwtService jwtService;

    @Test
    @DisplayName("POST /api/auth/users creates user and returns 201 Created")
    void shouldCreateUserSuccessfully() throws Exception {
        User createdUser = User.builder()
                .username("nam.le")
                .fullName("Lê Hoàng Nam")
                .email("nam.le@cruise.com")
                .role(Role.PASSENGER)
                .cabinNumber("Cabin 505")
                .rfidTag("RFID-88102")
                .active(true)
                .build();
        createdUser.setId(1L);

        when(userService.registerUser(any(User.class))).thenReturn(createdUser);

        String jsonPayload = """
                {
                    "username": "nam.le",
                    "fullName": "Lê Hoàng Nam",
                    "email": "nam.le@cruise.com",
                    "role": "PASSENGER",
                    "cabinNumber": "Cabin 505",
                    "rfidTag": "RFID-88102"
                }
                """;

        mockMvc.perform(post("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.username").value("nam.le"))
                .andExpect(jsonPath("$.data.fullName").value("Lê Hoàng Nam"))
                .andExpect(jsonPath("$.data.role").value("PASSENGER"));
    }

    @Test
    @DisplayName("POST /api/auth/users returns 400 Bad Request when username exists")
    void shouldReturnBadRequestWhenUsernameExists() throws Exception {
        when(userService.registerUser(any(User.class)))
                .thenThrow(new IllegalArgumentException("Username already exists: nam.le"));

        String jsonPayload = """
                {
                    "username": "nam.le",
                    "fullName": "Lê Hoàng Nam",
                    "email": "nam.le@cruise.com",
                    "role": "PASSENGER"
                }
                """;

        mockMvc.perform(post("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Username already exists: nam.le"));
    }

    @Test
    @DisplayName("GET /api/auth/users/{username} returns 200 OK when user exists")
    void shouldGetUserByUsername() throws Exception {
        User user = User.builder()
                .username("nam.le")
                .fullName("Lê Hoàng Nam")
                .email("nam.le@cruise.com")
                .role(Role.PASSENGER)
                .build();
        user.setId(1L);

        when(userService.getUserByUsername("nam.le")).thenReturn(Optional.of(user));

        mockMvc.perform(get("/api/auth/users/nam.le").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("nam.le"))
                .andExpect(jsonPath("$.data.email").value("nam.le@cruise.com"));
    }

    @Test
    @DisplayName("GET /api/auth/users/{username} returns 404 Not Found when user does not exist")
    void shouldReturnNotFoundWhenUserDoesNotExist() throws Exception {
        when(userService.getUserByUsername("unknown.user")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/users/unknown.user").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("User not found: unknown.user"));
    }

    @Test
    @DisplayName("Direct test for handleIllegalArgument exception handler")
    void shouldHandleIllegalArgumentDirectly() {
        UserController controller = new UserController(userService);
        org.springframework.http.ResponseEntity<com.cruise.common.dto.ApiResponse<Void>> response =
                controller.handleIllegalArgument(new IllegalArgumentException("Custom bad request message"));
        org.junit.jupiter.api.Assertions.assertEquals(
                org.springframework.http.HttpStatus.BAD_REQUEST, response.getStatusCode());
        org.junit.jupiter.api.Assertions.assertEquals(
                "Custom bad request message", response.getBody().getMessage());
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 401 Unauthorized when Authorization header is missing")
    void shouldReturnUnauthorizedWhenDeleteUserWithoutToken() throws Exception {
        mockMvc.perform(delete("/api/auth/users/testuser"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Missing or invalid Authorization header"));
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 401 Unauthorized when token is invalid")
    void shouldReturnUnauthorizedWhenDeleteUserWithInvalidToken() throws Exception {
        when(jwtService.validateToken("bad-token")).thenReturn(false);

        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Invalid or expired authentication token"));
        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).deleteUserByUsername("testuser");
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 403 Forbidden when caller is not an administrator")
    void shouldReturnForbiddenWhenDeleteUserNonAdmin() throws Exception {
        when(jwtService.validateToken("staff-token")).thenReturn(true);
        when(jwtService.extractRole("staff-token")).thenReturn("STAFF");

        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Bearer staff-token"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("Access denied: Administrator privileges required"));
        org.mockito.Mockito.verify(userService, org.mockito.Mockito.never()).deleteUserByUsername("testuser");
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 200 OK when caller is ADMIN")
    void shouldDeleteUserSuccessfullyWhenAdmin() throws Exception {
        when(jwtService.validateToken("admin-token")).thenReturn(true);
        when(jwtService.extractRole("admin-token")).thenReturn("ADMIN");

        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Bearer admin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));
        org.mockito.Mockito.verify(userService).deleteUserByUsername("testuser");
    }

    private User sampleUser(Long id, String username, Role role, boolean active) {
        User user = User.builder()
                .username(username)
                .fullName("Người dùng " + username)
                .email(username + "@cruise.com")
                .role(role)
                .active(active)
                .build();
        user.setId(id);
        return user;
    }

    @Test
    @DisplayName("GET /api/auth/users returns the list of users")
    void shouldListUsers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(
                sampleUser(1L, "an.nguyen", Role.SYSTEM_ADMIN, true),
                sampleUser(2L, "binh.tran", Role.POS_STAFF, false)));

        mockMvc.perform(get("/api/auth/users").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].username").value("an.nguyen"))
                .andExpect(jsonPath("$.data[1].active").value(false));
    }

    @Test
    @DisplayName("GET /api/auth/users/id/{id} returns 200 when found and 404 when missing")
    void shouldGetUserById() throws Exception {
        when(userService.getUserById(1L)).thenReturn(Optional.of(sampleUser(1L, "an.nguyen", Role.SYSTEM_ADMIN, true)));
        when(userService.getUserById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/auth/users/id/1").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.role").value("SYSTEM_ADMIN"));

        mockMvc.perform(get("/api/auth/users/id/99").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 99"));
    }

    @Test
    @DisplayName("PUT /api/auth/users/{id} updates a user")
    void shouldUpdateUser() throws Exception {
        User updated = sampleUser(1L, "an.nguyen", Role.POS_STAFF, true);
        updated.setFullName("An Nguyễn");
        when(userService.updateUser(eq(1L), eq("An Nguyễn"), eq("an.nguyen@cruise.com"), eq(Role.POS_STAFF)))
                .thenReturn(updated);

        String payload = """
                {"fullName": "An Nguyễn", "email": "an.nguyen@cruise.com", "role": "POS_STAFF"}
                """;

        mockMvc.perform(put("/api/auth/users/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User updated successfully"))
                .andExpect(jsonPath("$.data.fullName").value("An Nguyễn"))
                .andExpect(jsonPath("$.data.role").value("POS_STAFF"));
    }

    @Test
    @DisplayName("PUT /api/auth/users/{id} returns 404 for a missing user and 400 for a duplicate email")
    void shouldMapUpdateErrors() throws Exception {
        when(userService.updateUser(eq(99L), any(), any(), any()))
                .thenThrow(new NoSuchElementException("User not found: 99"));
        when(userService.updateUser(eq(2L), any(), any(), any()))
                .thenThrow(new IllegalArgumentException("Email already exists: taken@cruise.com"));

        String payload = """
                {"fullName": "A", "email": "taken@cruise.com", "role": "PASSENGER"}
                """;

        mockMvc.perform(put("/api/auth/users/99").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("User not found: 99"));

        mockMvc.perform(put("/api/auth/users/2").contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email already exists: taken@cruise.com"));
    }

    @Test
    @DisplayName("PATCH /api/auth/users/{id}/deactivate and /activate toggle the account")
    void shouldActivateAndDeactivateUser() throws Exception {
        when(userService.setUserActive(1L, false)).thenReturn(sampleUser(1L, "an.nguyen", Role.POS_STAFF, false));
        when(userService.setUserActive(1L, true)).thenReturn(sampleUser(1L, "an.nguyen", Role.POS_STAFF, true));

        mockMvc.perform(patch("/api/auth/users/1/deactivate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(false));

        mockMvc.perform(patch("/api/auth/users/1/activate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("PATCH /api/auth/users/{id}/activate returns 404 when the user is missing")
    void shouldReturnNotFoundWhenTogglingMissingUser() throws Exception {
        when(userService.setUserActive(99L, true)).thenThrow(new NoSuchElementException("User not found: 99"));

        mockMvc.perform(patch("/api/auth/users/99/activate"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }
}

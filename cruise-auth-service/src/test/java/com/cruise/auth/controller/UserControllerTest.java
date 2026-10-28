package com.cruise.auth.controller;

import com.cruise.auth.dto.UserDto;
import com.cruise.auth.entity.User;
import com.cruise.auth.service.JwtService;
import com.cruise.auth.service.UserService;
import com.cruise.common.dto.ApiResponse;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
    private JwtService jwtService;

    @Test
    @DisplayName("POST /api/auth/users creates user with active flag and returns 201 Created")
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
                    "rfidTag": "RFID-88102",
                    "active": true
                }
                """;

        mockMvc.perform(post("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User created successfully"))
                .andExpect(jsonPath("$.data.username").value("nam.le"))
                .andExpect(jsonPath("$.data.fullName").value("Lê Hoàng Nam"))
                .andExpect(jsonPath("$.data.role").value("PASSENGER"))
                .andExpect(jsonPath("$.data.active").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/users defaults to PASSENGER when role is omitted or null")
    void shouldDefaultToPassengerWhenRoleIsNull() throws Exception {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User savedUser = User.builder().username("norole").role(Role.PASSENGER).build();
        when(userService.registerUser(captor.capture())).thenReturn(savedUser);

        mockMvc.perform(post("/api/auth/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"norole\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("PASSENGER"));

        assertEquals(Role.PASSENGER, captor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/users defaults to PASSENGER when caller provides non-bearer or invalid token")
    void shouldDefaultToPassengerWhenCallerHasInvalidToken() throws Exception {
        when(jwtService.validateToken("invalid-token")).thenReturn(false);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User savedUser = User.builder().username("intruder").role(Role.PASSENGER).build();
        when(userService.registerUser(captor.capture())).thenReturn(savedUser);

        // Non-bearer header
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Basic dXNlcjpwYXNz")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"intruder\", \"role\": \"SYSTEM_ADMIN\"}"))
                .andExpect(status().isCreated());
        assertEquals(Role.PASSENGER, captor.getValue().getRole());

        // Invalid token
        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer invalid-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"intruder\", \"role\": \"SYSTEM_ADMIN\"}"))
                .andExpect(status().isCreated());
        assertEquals(Role.PASSENGER, captor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/users defaults to PASSENGER when token has non-admin role or null role")
    void shouldDefaultToPassengerWhenTokenHasNonAdminRole() throws Exception {
        when(jwtService.validateToken("staff-token")).thenReturn(true);
        when(jwtService.extractRole("staff-token")).thenReturn("STAFF");
        when(jwtService.validateToken("norole-token")).thenReturn(true);
        when(jwtService.extractRole("norole-token")).thenReturn(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User savedUser = User.builder().username("test").role(Role.PASSENGER).build();
        when(userService.registerUser(captor.capture())).thenReturn(savedUser);

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer staff-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"test\", \"role\": \"SYSTEM_ADMIN\"}"))
                .andExpect(status().isCreated());
        assertEquals(Role.PASSENGER, captor.getValue().getRole());

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer norole-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"test\", \"role\": \"SYSTEM_ADMIN\"}"))
                .andExpect(status().isCreated());
        assertEquals(Role.PASSENGER, captor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/users permits privileged role when admin provides valid Bearer token")
    void shouldPermitPrivilegedRoleWhenAdminRequestsIt() throws Exception {
        when(jwtService.validateToken("valid-admin-token")).thenReturn(true);
        when(jwtService.extractRole("valid-admin-token")).thenReturn("SYSTEM_ADMIN");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User savedUser = User.builder()
                .username("newadmin")
                .role(Role.SYSTEM_ADMIN)
                .build();
        savedUser.setId(3L);
        when(userService.registerUser(captor.capture())).thenReturn(savedUser);

        String jsonPayload = """
                {
                    "username": "newadmin",
                    "role": "SYSTEM_ADMIN"
                }
                """;

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer valid-admin-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(jsonPayload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("SYSTEM_ADMIN"));

        assertEquals(Role.SYSTEM_ADMIN, captor.getValue().getRole());
    }

    @Test
    @DisplayName("POST /api/auth/users permits privileged role when caller has ADMIN token")
    void shouldPermitPrivilegedRoleWhenAdminTokenRoleIsAdmin() throws Exception {
        when(jwtService.validateToken("admin-role-token")).thenReturn(true);
        when(jwtService.extractRole("admin-role-token")).thenReturn("ADMIN");

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        User savedUser = User.builder().username("adminuser").role(Role.OPERATIONS_MANAGER).build();
        when(userService.registerUser(captor.capture())).thenReturn(savedUser);

        mockMvc.perform(post("/api/auth/users")
                        .header("Authorization", "Bearer admin-role-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"adminuser\", \"role\": \"OPERATIONS_MANAGER\"}"))
                .andExpect(status().isCreated());
        assertEquals(Role.OPERATIONS_MANAGER, captor.getValue().getRole());
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
        ResponseEntity<ApiResponse<Void>> response =
                controller.handleIllegalArgument(new IllegalArgumentException("Custom bad request message"));
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals("Custom bad request message", response.getBody().getMessage());
    }

    @Test
    @DisplayName("Direct test for UserController without jwtService")
    void shouldHandleDirectControllerCallsWithoutJwtService() {
        UserController controllerWithoutJwt = new UserController(userService);
        ResponseEntity<?> res = controllerWithoutJwt.deleteUser("test", "Bearer some-token");
        assertEquals(HttpStatus.UNAUTHORIZED, res.getStatusCode());

        UserDto dto = new UserDto();
        dto.setUsername("guest");
        dto.setRole(Role.SYSTEM_ADMIN);
        when(userService.registerUser(any())).thenReturn(User.builder().username("guest").role(Role.PASSENGER).build());
        ResponseEntity<?> createRes = controllerWithoutJwt.createUser(dto, "Bearer token");
        assertEquals(HttpStatus.CREATED, createRes.getStatusCode());
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
    @DisplayName("DELETE /api/auth/users/{username} returns 401 when Authorization header is not Bearer")
    void shouldReturnUnauthorizedWhenHeaderIsNotBearer() throws Exception {
        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Basic 12345"))
                .andExpect(status().isUnauthorized())
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
        verify(userService, never()).deleteUserByUsername("testuser");
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
        verify(userService, never()).deleteUserByUsername("testuser");
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 403 when token role is null")
    void shouldReturnForbiddenWhenTokenRoleIsNull() throws Exception {
        when(jwtService.validateToken("valid-token")).thenReturn(true);
        when(jwtService.extractRole("valid-token")).thenReturn(null);

        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isForbidden());
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
        verify(userService).deleteUserByUsername("testuser");
    }

    @Test
    @DisplayName("DELETE /api/auth/users/{username} returns 200 OK when caller is SYSTEM_ADMIN")
    void shouldDeleteUserSuccessfullyWhenSystemAdmin() throws Exception {
        when(jwtService.validateToken("sysadmin-token")).thenReturn(true);
        when(jwtService.extractRole("sysadmin-token")).thenReturn("SYSTEM_ADMIN");

        mockMvc.perform(delete("/api/auth/users/testuser")
                        .header("Authorization", "Bearer sysadmin-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("User deleted successfully"));
        verify(userService).deleteUserByUsername("testuser");
    }
}

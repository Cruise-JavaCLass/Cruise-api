package com.cruise.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO đăng nhập
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginRequest {

    @NotBlank(message = "Username hoặc Email không được để trống")
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;
}

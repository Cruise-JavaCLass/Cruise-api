package com.cruise.auth.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

/**
 * Request DTO kiểm tra xác minh mật khẩu
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class VerifyPasswordRequest {

    @NotBlank(message = "Username hoặc Email không được để trống")
    @JsonAlias({"username", "email"})
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu cần xác minh không được để trống")
    @JsonAlias({"currentPassword", "rawPassword"})
    private String password;
}

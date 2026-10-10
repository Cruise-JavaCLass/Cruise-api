package com.cruise.auth.dto;

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
    private String usernameOrEmail;

    @NotBlank(message = "Mật khẩu cần xác minh không được để trống")
    private String password;
}

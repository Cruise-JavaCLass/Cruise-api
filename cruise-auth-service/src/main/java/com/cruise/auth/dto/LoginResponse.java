package com.cruise.auth.dto;

import com.cruise.auth.entity.User;
import lombok.*;

/**
 * Response DTO trả về sau khi đăng nhập thành công hoặc làm mới token
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;

    private String refreshToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;

    private Long refreshExpiresIn;

    private User user;
}

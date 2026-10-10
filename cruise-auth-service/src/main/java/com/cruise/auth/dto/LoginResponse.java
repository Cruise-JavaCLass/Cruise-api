package com.cruise.auth.dto;

import com.cruise.auth.entity.User;
import lombok.*;

/**
 * Response DTO trả về sau khi đăng nhập thành công
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoginResponse {

    private String accessToken;

    @Builder.Default
    private String tokenType = "Bearer";

    private Long expiresIn;

    private User user;
}

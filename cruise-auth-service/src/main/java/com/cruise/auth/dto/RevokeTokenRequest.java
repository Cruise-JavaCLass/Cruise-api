package com.cruise.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để yêu cầu thu hồi token (đăng xuất khỏi thiết bị hoặc thu hồi cưỡng chế)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevokeTokenRequest {

    @NotBlank(message = "Token must not be blank")
    private String token;

    private String tokenTypeHint; // ACCESS hoặc REFRESH (tùy chọn)
}

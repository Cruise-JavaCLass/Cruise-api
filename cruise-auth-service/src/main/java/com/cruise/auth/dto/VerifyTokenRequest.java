package com.cruise.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO để yêu cầu kiểm tra và giải mã xác minh thông tin token
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerifyTokenRequest {

    @NotBlank(message = "Token must not be blank")
    private String token;
}

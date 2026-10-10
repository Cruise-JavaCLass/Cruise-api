package com.cruise.auth.dto;

import lombok.*;

/**
 * Request DTO đăng xuất (tùy chọn gửi token trong body)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LogoutRequest {
    private String token;
}

package com.cruise.auth.dto;

import com.cruise.common.enums.Role;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dữ liệu cho phép cập nhật của một tài khoản (không đổi username và mật khẩu)
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateDto {
    private String fullName;
    private String email;
    private Role role;
}

package com.cruise.auth.dto;

import com.cruise.common.enums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserResponseDto {
    private Long id;
    private String username;
    private String fullName;
    private String email;
    private Role role;
    private String cabinNumber;
    private String rfidTag;
    private Boolean active;
    private Instant createdAt;
    private Instant updatedAt;
}

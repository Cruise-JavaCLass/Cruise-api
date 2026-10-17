package com.cruise.auth.dto;

import com.cruise.common.enums.Role;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private String username;
    private String password;
    private String fullName;
    private String email;
    private Role role;
    private String cabinNumber;
    private String rfidTag;
    private Boolean active = true;
}

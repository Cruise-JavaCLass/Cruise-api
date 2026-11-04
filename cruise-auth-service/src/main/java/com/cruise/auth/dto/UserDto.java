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
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(min = 3, max = 50)
    private String username;
    private String password;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Size(max = 100)
    private String fullName;
    @jakarta.validation.constraints.NotBlank
    @jakarta.validation.constraints.Email
    @jakarta.validation.constraints.Size(max = 100)
    private String email;
    @jakarta.validation.constraints.NotNull
    private Role role;
    private String cabinNumber;
    private String rfidTag;

    @Builder.Default
    private Boolean active = true;
}

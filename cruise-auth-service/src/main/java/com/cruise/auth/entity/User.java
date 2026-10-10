package com.cruise.auth.entity;

import com.cruise.common.enums.Role;
import com.cruise.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Entity đại diện cho người dùng và hành khách trong hệ sinh thái Cruise
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User extends BaseEntity {

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Role role;

    @Column(name = "cabin_number", length = 20)
    private String cabinNumber;

    @Column(name = "rfid_tag", length = 50)
    private String rfidTag;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}

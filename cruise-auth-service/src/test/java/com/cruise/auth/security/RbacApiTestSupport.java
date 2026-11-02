package com.cruise.auth.security;

import com.cruise.auth.entity.User;
import com.cruise.auth.repository.UserRepository;
import com.cruise.auth.service.JwtService;
import com.cruise.common.enums.Role;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
abstract class RbacApiTestSupport {
    @Autowired protected MockMvc mvc;
    @Autowired protected UserRepository users;
    @Autowired protected JwtService jwt;

    @BeforeEach void clearRevocations() { jwt.clearRevocations(); }

    protected User account(Role role) {
        return users.saveAndFlush(User.builder().username("rbac." + role.name().toLowerCase())
                .fullName("RBAC Test").email(role.name().toLowerCase() + "@rbac.test")
                .role(role).active(true).build());
    }

    protected String bearer(User user) { return "Bearer " + jwt.generateAccessToken(user); }
}

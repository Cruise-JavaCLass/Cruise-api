package com.cruise.pos.config;

import com.cruise.pos.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Bảo mật chuẩn cho module POS: stateless JWT, không session, không CSRF.
 *
 * <p>Bật bằng {@code pos.security.enabled=true} sau khi toàn bộ client đã gửi JWT.
 * Khi tắt, {@link DisabledSecurityConfig} permit-all để không phá vỡ luồng demo hiện tại.</p>
 */
@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pos.security.enabled", havingValue = "true")
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    @SuppressWarnings("java:S4502") // REST API sử dụng Stateless JWT Authentication nên CSRF protection được tắt an toàn
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/pos/auth/manifest/**").hasAnyRole("POS_STAFF", "OPERATIONS_MANAGER", "SYSTEM_ADMIN")
                        .requestMatchers("/api/admin/voyage-lifecycle/**").hasAnyRole("OPERATIONS_MANAGER", "SYSTEM_ADMIN", "ADMIN")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}

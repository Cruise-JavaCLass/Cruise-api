package com.cruise.pos.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Cấu hình bảo mật mở (permit-all) khi {@code pos.security.enabled} không bật.
 *
 * <p>Đây là trạng thái mặc định trong giai đoạn chuyển tiếp: frontend chưa có
 * luồng đăng nhập nên nếu bật JWT sẽ chặn toàn bộ màn hình POS. Bật cờ này cùng
 * với {@code JWT_SECRET} là đủ để bảo vệ API.</p>
 */
@Configuration
@ConditionalOnProperty(name = "pos.security.enabled", havingValue = "false", matchIfMissing = false)
public class DisabledSecurityConfig {

    @Bean
    @SuppressWarnings("java:S4502") // Chế độ unauthenticated demo-only/internal development, tắt CSRF có chủ đích
    public SecurityFilterChain permissiveSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/pos/terminals/**").denyAll()
                        .anyRequest().permitAll());
        return http.build();
    }
}

package com.cruise.auth.config;

import com.cruise.common.security.RbacFilterConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(RbacFilterConfiguration.class)
public class RbacConfig {
}

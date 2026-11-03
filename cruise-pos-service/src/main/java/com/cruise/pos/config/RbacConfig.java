package com.cruise.pos.config;

import com.cruise.common.security.RemoteRbacConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/** Runs even when the legacy POS demo security switch is disabled. */
@Configuration(proxyBeanMethods = false)
@Import(RemoteRbacConfiguration.class)
public class RbacConfig {
}

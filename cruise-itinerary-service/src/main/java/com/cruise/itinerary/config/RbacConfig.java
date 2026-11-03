package com.cruise.itinerary.config;

import com.cruise.common.security.RemoteRbacConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration(proxyBeanMethods = false)
@Import(RemoteRbacConfiguration.class)
public class RbacConfig {
}

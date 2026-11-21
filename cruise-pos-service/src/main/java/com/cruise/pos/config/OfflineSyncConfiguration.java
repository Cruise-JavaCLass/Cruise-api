package com.cruise.pos.config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
@Configuration(proxyBeanMethods=false)
public class OfflineSyncConfiguration {
    @Bean public Clock offlineSyncClock() { return Clock.systemUTC(); }
}

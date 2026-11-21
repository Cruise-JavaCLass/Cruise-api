package com.cruise.activity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.time.Clock;

@Configuration(proxyBeanMethods=false)
@EnableAsync
@EnableScheduling
public class SafetyTelemetryConfiguration {
    @Bean public Clock safetyClock() { return Clock.systemUTC(); }
    @Bean(name="safetyTelemetryExecutor")
    public ThreadPoolTaskExecutor safetyTelemetryExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1); executor.setMaxPoolSize(1); executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("safety-telemetry-");
        executor.setWaitForTasksToCompleteOnShutdown(true); executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}

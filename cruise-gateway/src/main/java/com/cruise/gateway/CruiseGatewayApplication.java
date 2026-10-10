package com.cruise.gateway;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.TimeZone;

@SpringBootApplication
public class CruiseGatewayApplication {

    private static final Logger log = LoggerFactory.getLogger(CruiseGatewayApplication.class);
    private final Environment env;

    public CruiseGatewayApplication(Environment env) {
        this.env = env;
    }

    @PostConstruct
    public void init() {
        // Cấu hình chuẩn múi giờ mặc định UTC cho toàn bộ hệ thống
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        log.info("System default timezone set to UTC");
    }

    public static void main(String[] args) {
        SpringApplication.run(CruiseGatewayApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        String serverPort = env.getProperty("server.port", "8080");
        String contextPath = env.getProperty("server.servlet.context-path", "");
        String hostAddress = "localhost";
        try {
            hostAddress = InetAddress.getLocalHost().getHostAddress();
        } catch (UnknownHostException ignored) {}

        log.info("\n----------------------------------------------------------\n\t" +
                        "Cruise Activity & Service Management System (Gateway) is running!\n\t" +
                        "Local: \t\thttp://localhost:{}{}\n\t" +
                        "External: \thttp://{}:{}{}\n\t" +
                        "Health: \thttp://localhost:{}{}/api/health\n\t" +
                        "Info: \t\thttp://localhost:{}{}/api/info\n\t" +
                        "Ping: \t\thttp://localhost:{}{}/api/ping\n" +
                        "----------------------------------------------------------",
                serverPort, contextPath,
                hostAddress, serverPort, contextPath,
                serverPort, contextPath,
                serverPort, contextPath,
                serverPort, contextPath);
    }
}

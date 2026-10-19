package com.cruise.pos.config;

import com.cruise.pos.service.PosFolioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"dev", "test"})
@RequiredArgsConstructor
@Slf4j
public class PosFolioSeeder implements ApplicationRunner {

    private final PosFolioService folioService;

    @Override
    public void run(ApplicationArguments args) {
        log.info("Running PosFolioSeeder for dev/test environment...");
        folioService.seedDefaultFolios();
    }
}

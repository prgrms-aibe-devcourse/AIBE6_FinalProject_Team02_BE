package com.backend_catcheat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
@EnableAsync
public class BackendCatcheatApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendCatcheatApplication.class, args);
    }

}

package com.backend_catcheat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableJpaAuditing
public class BackendCatcheatApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendCatcheatApplication.class, args);
    }

}

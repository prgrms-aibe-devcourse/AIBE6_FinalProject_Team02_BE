package com.backend_catcheat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class BackendCatcheatApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackendCatcheatApplication.class, args);
    }

}

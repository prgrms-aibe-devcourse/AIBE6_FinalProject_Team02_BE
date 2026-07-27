package com.backend_catcheat.spike.vision.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Profile("dev")
@Configuration
@EnableWebSecurity
public class SpikeSecurityConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain spikeSecurityFilterChain(HttpSecurity http) throws Exception {
        return http
                .securityMatcher("/api/v1/spike/**")
                .csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .build();
    }
}

package com.fiscalhub.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FiscalHubBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(FiscalHubBackendApplication.class, args);
    }
}

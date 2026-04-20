package com.micuota.mvp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MicuotaMvpApplication {

    public static void main(String[] args) {
        SpringApplication.run(MicuotaMvpApplication.class, args);
    }
}

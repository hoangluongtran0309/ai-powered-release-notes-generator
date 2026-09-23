package com.hoangluongtran0309.releaseflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ReleaseFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReleaseFlowApplication.class, args);
    }
}

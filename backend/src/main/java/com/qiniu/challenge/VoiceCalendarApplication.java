package com.qiniu.challenge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class VoiceCalendarApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceCalendarApplication.class, args);
    }
}

package com.tuhmb.smartattendancebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartAttendanceBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartAttendanceBackendApplication.class, args);
    }

}

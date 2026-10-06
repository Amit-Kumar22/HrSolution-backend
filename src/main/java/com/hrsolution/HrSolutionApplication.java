package com.hrsolution;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HrSolutionApplication {

    public static void main(String[] args) {
        SpringApplication.run(HrSolutionApplication.class, args);
    }
}

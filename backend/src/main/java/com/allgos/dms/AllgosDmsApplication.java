package com.allgos.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class AllgosDmsApplication {

    public static void main(String[] args) {
        SpringApplication.run(AllgosDmsApplication.class, args);
    }
}

package com.testingai.sdlc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SdlcAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(SdlcAgentApplication.class, args);
    }
}

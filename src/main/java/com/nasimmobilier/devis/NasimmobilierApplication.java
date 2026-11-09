package com.nasimmobilier.devis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NasimmobilierApplication {

    public static void main(String[] args) {
        SpringApplication.run(NasimmobilierApplication.class, args);
    }

}

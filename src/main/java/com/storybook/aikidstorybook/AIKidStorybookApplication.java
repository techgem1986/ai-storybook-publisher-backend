package com.storybook.aikidstorybook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EntityScan(basePackages = "com.storybook.aikidstorybook.entity")
@EnableJpaRepositories(basePackages = "com.storybook.aikidstorybook.repository")
public class AIKidStorybookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIKidStorybookApplication.class, args);
    }

}
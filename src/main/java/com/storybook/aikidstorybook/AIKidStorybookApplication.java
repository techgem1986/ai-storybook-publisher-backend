package com.storybook.aikidstorybook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableRetry
@EnableScheduling
@EntityScan(basePackages = "com.storybook.aikidstorybook.entity")
@EnableJpaRepositories(basePackages = "com.storybook.aikidstorybook.repository")
public class AIKidStorybookApplication {

    public static void main(String[] args) {
        SpringApplication.run(AIKidStorybookApplication.class, args);
    }

}
package io.github.fabricetiennette.radiofy.backend;

import org.springframework.boot.SpringApplication;

public class TestRadiofyBackendApplication {

    public static void main(String[] args) {
        SpringApplication.from(RadiofyBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

package br.com.thiagobianeck.awss3poc;

import org.springframework.boot.SpringApplication;

public class TestAwss3pocApplication {

    public static void main(String[] args) {
        SpringApplication.from(Awss3pocApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

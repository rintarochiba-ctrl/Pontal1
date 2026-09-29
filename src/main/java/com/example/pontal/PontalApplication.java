package com.example.pontal;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.example.pontal.mapper")
public class PontalApplication {
    public static void main(String[] args) {
        SpringApplication.run(PontalApplication.class, args);
    }
}

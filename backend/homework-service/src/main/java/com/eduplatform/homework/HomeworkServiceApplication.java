package com.eduplatform.homework;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@MapperScan("com.eduplatform.homework.mapper")
@org.springframework.context.annotation.ComponentScan(basePackages = { "com.eduplatform.homework",
        "com.eduplatform.common" })
public class HomeworkServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(HomeworkServiceApplication.class, args);
    }
}

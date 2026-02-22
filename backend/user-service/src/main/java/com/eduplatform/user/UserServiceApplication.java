package com.eduplatform.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.UserMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.LocalDateTime;

/**
 * 用户服务启动入口。
 * 设计目标：注册为微服务并启用调度能力，同时在开发/测试环境提供管理员初始化能力。
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
@MapperScan("com.eduplatform.user.mapper")
@org.springframework.context.annotation.ComponentScan(basePackages = { "com.eduplatform.user",
        "com.eduplatform.common" })
public class UserServiceApplication {
    /**
     * 应用主入口。
     */
    public static void main(String[] args) {
        SpringApplication.run(UserServiceApplication.class, args);
    }

    /**
     * 管理员账号引导初始化。
     * 业务原因：开发与测试环境需要稳定的默认管理员账号，避免手工插库造成数据不一致。
     * 说明：仅在配置开关开启时执行，避免生产环境产生非预期账号。
     */
    @Bean
    public CommandLineRunner bootstrapAdmin(UserMapper userMapper, Environment environment) {
        return args -> {
            Boolean enabled = environment.getProperty("bootstrap.admin.enabled", Boolean.class, false);
            if (!enabled) {
                return;
            }

            BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

            String email = "admin_local@edu-platform.local";
            String usernameBase = "admin_local";
            String rawPassword = environment.getProperty("bootstrap.admin.initial-password");
            if (rawPassword == null || rawPassword.isBlank()) {
                throw new IllegalStateException("bootstrap.admin.enabled=true 时必须配置 bootstrap.admin.initial-password");
            }

            User existByEmail = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
            if (existByEmail != null) {
                existByEmail.setRole("admin");
                existByEmail.setStatus(1);
                existByEmail.setPassword(encoder.encode(rawPassword));
                if (existByEmail.getCreatedAt() == null) {
                    existByEmail.setCreatedAt(LocalDateTime.now());
                }
                existByEmail.setUpdatedAt(LocalDateTime.now());
                userMapper.updateById(existByEmail);
                return;
            }

            String username = usernameBase;
            int i = 0;
            while (userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getUsername, username)) > 0) {
                i++;
                username = usernameBase + i;
            }

            User admin = new User();
            admin.setEmail(email);
            admin.setUsername(username);
            admin.setName("管理员");
            admin.setPassword(encoder.encode(rawPassword));
            admin.setRole("admin");
            admin.setStatus(1);
            admin.setCreatedAt(LocalDateTime.now());
            admin.setUpdatedAt(LocalDateTime.now());
            userMapper.insert(admin);
        };
    }
}

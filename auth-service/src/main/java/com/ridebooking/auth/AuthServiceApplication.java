package com.ridebooking.auth;

import com.ridebooking.auth.client.UserServiceClientProperties;
import com.ridebooking.auth.config.AdminBootstrapProperties;
import com.ridebooking.auth.config.EmailProperties;
import com.ridebooking.auth.config.JwtProperties;
import com.ridebooking.auth.config.LoginSecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({JwtProperties.class, UserServiceClientProperties.class,
        AdminBootstrapProperties.class, LoginSecurityProperties.class, EmailProperties.class})
public class AuthServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}

}
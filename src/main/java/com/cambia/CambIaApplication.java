package com.cambia;

import com.cambia.auth.CambiaMailProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CambiaMailProperties.class)
public class CambIaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CambIaApplication.class, args);
	}

}

package com.cambia;

import com.cambia.auth.CambiaMailProperties;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
// Envio do link mágico roda em segundo plano (MagicLinkEnvioAssincrono) — achado de
// revisão de segurança, ver com.cambia.auth.MagicLinkEnvioAssincrono.
@EnableAsync
@EnableConfigurationProperties(CambiaMailProperties.class)
public class CambIaApplication {

	public static void main(String[] args) {
		SpringApplication.run(CambIaApplication.class, args);
	}

}

package com.cambia;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relógio injetável (Incremento 79): expiração e último uso das chaves de API dependem do
 * "agora", e os testes precisam avançar o tempo sem dormir (ver RelogioAjustavelConfig nos
 * testes, que substitui este bean via @Primary).
 */
@Configuration(proxyBeanMethods = false)
class RelogioConfig {

	@Bean
	Clock relogio() {
		return Clock.systemUTC();
	}

}

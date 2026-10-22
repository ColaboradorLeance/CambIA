package com.cambia.chaveapi;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Relógio controlável pelos testes de chave de API: substitui (via @Primary) o Clock da
 * aplicação, pra simular expiração (A5) e a janela de 5 minutos do último uso (A8) sem
 * dormir de verdade. Só é importado por quem precisa — os demais testes continuam com o
 * relógio real.
 */
@TestConfiguration(proxyBeanMethods = false)
class RelogioAjustavelConfig {

	@Bean
	@Primary
	RelogioAjustavel relogioAjustavel() {
		return new RelogioAjustavel();
	}

	static class RelogioAjustavel extends Clock {

		private volatile Duration deslocamento = Duration.ZERO;

		void avancar(Duration quanto) {
			deslocamento = deslocamento.plus(quanto);
		}

		void zerar() {
			deslocamento = Duration.ZERO;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return Instant.now().plus(deslocamento);
		}

	}

}

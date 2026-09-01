package com.cambia.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LimitadorDeRequisicoesTests {

	@Test
	void permiteAteOLimiteDepoisBloqueiaEDesbloqueiaQuandoAJanelaPassa() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(3, Duration.ofMinutes(15), relogio);

		assertTrue(limitador.permitir("chave"));
		assertTrue(limitador.permitir("chave"));
		assertTrue(limitador.permitir("chave"));
		assertFalse(limitador.permitir("chave"), "a 4a tentativa dentro da janela deveria ser bloqueada");

		relogio.avancar(Duration.ofMinutes(16));
		assertTrue(limitador.permitir("chave"), "depois que a janela passa, libera de novo");
	}

	@Test
	void chavesDiferentesTemContadoresIndependentes() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(1, Duration.ofMinutes(15), relogio);

		assertTrue(limitador.permitir("chave-a"));
		assertFalse(limitador.permitir("chave-a"));
		assertTrue(limitador.permitir("chave-b"), "chave diferente não deveria ser afetada pelo limite da outra");
	}

	private static final class RelogioControlavel extends Clock {
		private Instant instante;

		RelogioControlavel(Instant inicial) {
			this.instante = inicial;
		}

		void avancar(Duration duracao) {
			instante = instante.plus(duracao);
		}

		@Override
		public ZoneId getZone() {
			return ZoneId.of("UTC");
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instante;
		}
	}

}

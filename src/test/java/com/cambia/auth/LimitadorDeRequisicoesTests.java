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

	// --- Achado de revisão de segurança: exaustão de memória (mapa nunca encolhia) ---

	@Test
	void limpezaRemoveChavesComTodasAsTentativasExpiradas() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(5, Duration.ofMinutes(15), relogio);

		limitador.permitir("email-aleatorio-1@ataque.com");
		limitador.permitir("email-aleatorio-2@ataque.com");
		limitador.permitir("email-aleatorio-3@ataque.com");
		assertTrue(limitador.chavesRastreadas() == 3);

		relogio.avancar(Duration.ofMinutes(16));
		limitador.limparChavesExpiradas();

		assertTrue(limitador.chavesRastreadas() == 0, "chaves sem tentativa válida deveriam ter sido removidas");
	}

	@Test
	void limpezaMantemChavesAindaDentroDaJanela() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(5, Duration.ofMinutes(15), relogio);

		limitador.permitir("expira");
		relogio.avancar(Duration.ofMinutes(16));
		limitador.permitir("ainda-ativa");

		limitador.limparChavesExpiradas();

		assertTrue(limitador.chavesRastreadas() == 1, "só a chave com tentativa dentro da janela deveria sobrar");
	}

	@Test
	void tetoDeChavesBloqueiaChaveNovaQuandoAtingidoMasNaoAfetaChavesJaConhecidas() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(5, Duration.ofMinutes(15), 2, relogio);

		assertTrue(limitador.permitir("chave-a"));
		assertTrue(limitador.permitir("chave-b"));
		assertFalse(limitador.permitir("chave-c"), "mapa cheio: uma chave nova deveria ser negada");

		// chaves já conhecidas continuam funcionando normalmente mesmo com o mapa cheio
		assertTrue(limitador.permitir("chave-a"));
	}

	@Test
	void limpezaLiberaEspacoNoTetoDepoisQueTentativasExpiram() {
		RelogioControlavel relogio = new RelogioControlavel(Instant.parse("2026-01-01T00:00:00Z"));
		LimitadorDeRequisicoes limitador = new LimitadorDeRequisicoes(5, Duration.ofMinutes(15), 1, relogio);

		assertTrue(limitador.permitir("chave-a"));
		assertFalse(limitador.permitir("chave-b"), "mapa cheio (teto=1)");

		relogio.avancar(Duration.ofMinutes(16));
		limitador.limparChavesExpiradas();

		assertTrue(limitador.permitir("chave-b"), "depois da limpeza, o espaço deveria estar livre de novo");
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

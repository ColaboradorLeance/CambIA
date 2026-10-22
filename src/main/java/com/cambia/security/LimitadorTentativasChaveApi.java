package com.cambia.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import com.cambia.auth.LimitadorDeRequisicoes;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Limite de tentativas com chave de API inválida, por IP (Incremento 80, pendência #18).
 *
 * <p>Instância PRÓPRIA do LimitadorDeRequisicoes, não a do login: a do login tem um teto de
 * chaves rastreadas compartilhado, e chaves inválidas vindas de muitos IPs o enchiam —
 * a partir daí /auth/magic-link respondia 429 pra qualquer e-mail novo. Um ataque à
 * integração não pode derrubar o login das pessoas.
 */
@Component
class LimitadorTentativasChaveApi {

	private final LimitadorDeRequisicoes limitador;

	LimitadorTentativasChaveApi(
			@Value("${cambia.rate-limit.chave-api.maximo:10}") int maximoPorJanela,
			@Value("${cambia.rate-limit.chave-api.janela-minutos:15}") long janelaMinutos,
			@Value("${cambia.rate-limit.chave-api.maximo-chaves-rastreadas:50000}") int maximoChavesRastreadas) {
		this.limitador = new LimitadorDeRequisicoes(maximoPorJanela, Duration.ofMinutes(janelaMinutos),
				maximoChavesRastreadas, Clock.systemUTC());
	}

	boolean permitir(String ip) {
		return limitador.permitir(ip);
	}

	// A instância não é um bean, então o @Scheduled dela não roda sozinho — a limpeza das
	// entradas expiradas (sem a qual o teto nunca se recupera) é agendada aqui.
	@Scheduled(fixedDelay = 5, timeUnit = TimeUnit.MINUTES)
	void limparExpiradas() {
		limitador.limparChavesExpiradas();
	}

}

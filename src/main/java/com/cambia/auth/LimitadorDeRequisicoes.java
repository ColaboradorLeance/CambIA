package com.cambia.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Achado de revisão de segurança: nenhum endpoint de autenticação tinha limite de
 * tentativas — {@code /auth/magic-link} podia ser usado pra "bombardear" o e-mail de
 * alguém, e nada limitava tentativas repetidas em {@code /auth/verify}. Janela deslizante
 * simples em memória — suficiente pra uma instância única (não distribuída) como esta
 * aplicação.
 */
@Component
class LimitadorDeRequisicoes {

	private final int maximoPorJanela;
	private final Duration janela;
	private final Clock relogio;
	private final ConcurrentMap<String, Deque<Instant>> historico = new ConcurrentHashMap<>();

	@Autowired
	LimitadorDeRequisicoes(
			@Value("${cambia.rate-limit.auth.maximo:5}") int maximoPorJanela,
			@Value("${cambia.rate-limit.auth.janela-minutos:15}") long janelaMinutos) {
		this(maximoPorJanela, Duration.ofMinutes(janelaMinutos), Clock.systemUTC());
	}

	LimitadorDeRequisicoes(int maximoPorJanela, Duration janela, Clock relogio) {
		this.maximoPorJanela = maximoPorJanela;
		this.janela = janela;
		this.relogio = relogio;
	}

	/**
	 * @return {@code true} se a requisição pode prosseguir; {@code false} se {@code chave}
	 * já atingiu o limite dentro da janela atual.
	 */
	boolean permitir(String chave) {
		Instant agora = Instant.now(relogio);
		Instant corteDaJanela = agora.minus(janela);
		Deque<Instant> tentativas = historico.computeIfAbsent(chave, k -> new ArrayDeque<>());
		synchronized (tentativas) {
			while (!tentativas.isEmpty() && tentativas.peekFirst().isBefore(corteDaJanela)) {
				tentativas.pollFirst();
			}
			if (tentativas.size() >= maximoPorJanela) {
				return false;
			}
			tentativas.addLast(agora);
			return true;
		}
	}

}

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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Achado de revisão de segurança: nenhum endpoint de autenticação tinha limite de
 * tentativas — {@code /auth/magic-link} podia ser usado pra "bombardear" o e-mail de
 * alguém, e nada limitava tentativas repetidas em {@code /auth/verify}. Janela deslizante
 * simples em memória — suficiente pra uma instância única (não distribuída) como esta
 * aplicação.
 *
 * <p>Achado de revisão de segurança ("olhar de hacker ético"): o mapa de histórico nunca
 * removia uma chave, mesmo depois de todas as tentativas dela expirarem — o limite por
 * chave protege contra repetição na MESMA chave, mas não impede um atacante de usar uma
 * chave nova a cada tentativa (ex.: um e-mail aleatório diferente por vez em
 * {@code POST /auth/magic-link} — cada um só é "visto" uma vez, então nunca esbarra no
 * próprio limite). Sem nunca remover nada, o mapa só cresce — exaustão de memória de longo
 * prazo. Duas camadas de correção: (1) {@link #limparChavesExpiradas()}, rodando
 * periodicamente, remove chaves cujas tentativas já expiraram todas, deixando o tamanho do
 * mapa proporcional ao tráfego realmente ativo dentro da janela, não ao total histórico
 * desde o boot; (2) um teto no número de chaves rastreadas ao mesmo tempo
 * ({@code maximoChavesRastreadas}), cinto-e-suspensório contra uma inundação rápida demais
 * pra limpeza periódica sozinha conter — sem o teto, um ataque concentrado num intervalo
 * curto ainda inflava o mapa livremente entre uma limpeza e outra.
 */
@Component
class LimitadorDeRequisicoes {

	private final int maximoPorJanela;
	private final Duration janela;
	private final int maximoChavesRastreadas;
	private final Clock relogio;
	private final ConcurrentMap<String, Deque<Instant>> historico = new ConcurrentHashMap<>();

	@Autowired
	LimitadorDeRequisicoes(
			@Value("${cambia.rate-limit.auth.maximo:5}") int maximoPorJanela,
			@Value("${cambia.rate-limit.auth.janela-minutos:15}") long janelaMinutos,
			@Value("${cambia.rate-limit.auth.maximo-chaves-rastreadas:50000}") int maximoChavesRastreadas) {
		this(maximoPorJanela, Duration.ofMinutes(janelaMinutos), maximoChavesRastreadas, Clock.systemUTC());
	}

	LimitadorDeRequisicoes(int maximoPorJanela, Duration janela, Clock relogio) {
		this(maximoPorJanela, janela, 50_000, relogio);
	}

	LimitadorDeRequisicoes(int maximoPorJanela, Duration janela, int maximoChavesRastreadas, Clock relogio) {
		this.maximoPorJanela = maximoPorJanela;
		this.janela = janela;
		this.maximoChavesRastreadas = maximoChavesRastreadas;
		this.relogio = relogio;
	}

	/**
	 * @return {@code true} se a requisição pode prosseguir; {@code false} se {@code chave}
	 * já atingiu o limite dentro da janela atual, ou se {@code chave} é nova e o teto de
	 * chaves rastreadas simultaneamente já foi atingido (ver a classe).
	 */
	boolean permitir(String chave) {
		Instant agora = Instant.now(relogio);
		Instant corteDaJanela = agora.minus(janela);

		Deque<Instant> tentativas = historico.get(chave);
		if (tentativas == null) {
			// Acima do teto: nega direto, sem criar entrada nova — é exatamente essa
			// criação sem fim que causava a exaustão de memória. Só afeta chaves NUNCA
			// vistas antes; uma chave já rastreada continua funcionando normalmente
			// mesmo com o mapa cheio.
			if (historico.size() >= maximoChavesRastreadas) {
				return false;
			}
			tentativas = historico.computeIfAbsent(chave, k -> new ArrayDeque<>());
		}
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

	// Achado de revisão de segurança (exaustão de memória): remove do mapa qualquer chave
	// cujas tentativas já expiraram todas — sem isso, o mapa só cresce, nunca encolhe. Além
	// de conter o crescimento no dia a dia, isso também é o que faz o teto de
	// maximoChavesRastreadas se recuperar sozinho depois de um pico (sem limpeza, uma vez
	// cheio, o mapa ficaria cheio pra sempre e passaria a bloquear até clientes legítimos).
	//
	// compute() é atômico por chave no ConcurrentHashMap (uma segunda chamada de
	// compute()/computeIfAbsent() pra MESMA chave espera esta terminar), e o
	// synchronized(tentativas) por dentro sincroniza com permitir() através do mesmo
	// objeto Deque — as duas travas nunca são adquiridas em ordem invertida (permitir() já
	// larga a trava do mapa antes de entrar no synchronized), então não há risco de
	// deadlock entre este método e permitir() rodando em paralelo.
	@Scheduled(fixedDelay = 5, timeUnit = java.util.concurrent.TimeUnit.MINUTES)
	void limparChavesExpiradas() {
		Instant corteDaJanela = Instant.now(relogio).minus(janela);
		for (String chave : historico.keySet()) {
			historico.compute(chave, (k, tentativas) -> {
				if (tentativas == null) {
					return null;
				}
				synchronized (tentativas) {
					while (!tentativas.isEmpty() && tentativas.peekFirst().isBefore(corteDaJanela)) {
						tentativas.pollFirst();
					}
					return tentativas.isEmpty() ? null : tentativas;
				}
			});
		}
	}

	// Só pra teste conseguir observar o efeito da limpeza/do teto sem expor o mapa inteiro.
	int chavesRastreadas() {
		return historico.size();
	}

}

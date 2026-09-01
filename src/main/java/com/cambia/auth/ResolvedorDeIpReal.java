package com.cambia.auth;

import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Achado de revisão de segurança: atrás do reverse-proxy (Fase 3A), {@code
 * HttpServletRequest.getRemoteAddr()} sempre devolvia o IP do container do proxy — nunca o
 * IP real de quem acessa —, quebrando o limite por IP de {@code /auth/verify} (Fase 2): na
 * prática virava um único limite compartilhado por todo mundo atrás do proxy.
 *
 * <p>Tentei resolver isso primeiro com {@code server.forward-headers-strategy=native} (o
 * jeito "padrão" do Spring Boot/Tomcat) — não funcionou de forma confiável nesta versão do
 * framework (confirmado com teste ao vivo: {@code getRemoteAddr()} continuava fixo mesmo
 * com a propriedade configurada). Resolução própria, no mesmo espírito do
 * {@code CsrfProtectionFilter} — mais simples de implementar e verificar corretamente.
 *
 * <p>Só confia no cabeçalho {@code X-Forwarded-For} quando quem conectou de fato (o
 * "salto" mais próximo, {@code getRemoteAddr()} cru) é um endereço de rede interna
 * (loopback ou faixa privada) — cobre exatamente o cenário de container-pra-container do
 * Docker Compose. Um IP externo de verdade não consegue forjar isso: pra chegar ao
 * backend, teria que passar pelo reverse-proxy, que sempre anexa o IP real de quem
 * conectou nele por último (não deixa o valor do cliente substituir, só acrescenta —
 * ver {@code reverse-proxy/nginx.conf}, variável {@code $proxy_add_x_forwarded_for}).
 */
final class ResolvedorDeIpReal {

	private static final Pattern ENDERECO_INTERNO = Pattern.compile(
			"^(127\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
					+ "|10\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}"
					+ "|192\\.168\\.\\d{1,3}\\.\\d{1,3}"
					+ "|172\\.(1[6-9]|2\\d|3[0-1])\\.\\d{1,3}\\.\\d{1,3}"
					+ "|::1|0:0:0:0:0:0:0:1)$");

	private ResolvedorDeIpReal() {
	}

	static String resolver(HttpServletRequest request) {
		String peerDireto = request.getRemoteAddr();
		String encaminhadoPor = request.getHeader("X-Forwarded-For");
		if (!ehInterno(peerDireto) || encaminhadoPor == null || encaminhadoPor.isBlank()) {
			return peerDireto;
		}
		// Pode ter vários IPs separados por vírgula — cada proxy confiável no caminho
		// anexa o IP de quem conectou nele. O da ponta direita é o mais recente; anda da
		// direita pra esquerda descartando entradas também internas (outros saltos de
		// proxy confiáveis), parando na primeira que não é (o cliente de verdade).
		String[] partes = encaminhadoPor.split(",");
		for (int i = partes.length - 1; i >= 0; i--) {
			String candidato = partes[i].trim();
			if (!candidato.isEmpty() && !ehInterno(candidato)) {
				return candidato;
			}
		}
		return peerDireto;
	}

	private static boolean ehInterno(String endereco) {
		return endereco != null && ENDERECO_INTERNO.matcher(endereco).matches();
	}

}

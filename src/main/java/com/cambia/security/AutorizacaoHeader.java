package com.cambia.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Achado de revisão de segurança ("olhar de hacker ético"): {@link SessaoAuthenticationFilter}
 * e {@link CsrfProtectionFilter} precisam concordar exatamente sobre o que conta como
 * "esta requisição autentica pelo header Authorization" — divergir nisso é frágil.
 * Antes desta classe, {@code CsrfProtectionFilter} isentava a requisição de CSRF só por
 * existir QUALQUER valor no header {@code Authorization} (bastava não ser nulo), enquanto
 * {@code SessaoAuthenticationFilter} só de fato autentica por esse header quando ele começa
 * com {@code "Bearer "}. Não era explorável hoje (o CORS restrito impede um site de
 * terceiros de completar essa requisição no navegador), mas era frágil: um valor qualquer
 * no header (ex.: {@code Authorization: qualquer-coisa}) bastava pra pular o CSRF, mesmo
 * quando a autenticação de fato ainda ia depender do cookie de sessão — a peça que o CSRF
 * existe justamente pra proteger.
 *
 * <p>Centralizando a extração aqui, os dois filtros nunca mais podem divergir: sempre que
 * o header for "Bearer-shaped" o suficiente pra isentar CSRF, ele também é,
 * necessariamente, o que {@code SessaoAuthenticationFilter} vai usar pra autenticar (e
 * nunca cai pro cookie nesse caso — ver o próprio filtro) — não há mais brecha onde CSRF é
 * pulado mas a autenticação de fato acaba caindo pro cookie.
 */
final class AutorizacaoHeader {

	private static final String PREFIXO_BEARER = "Bearer ";

	private AutorizacaoHeader() {
	}

	/**
	 * @return o token depois de {@code "Bearer "}, ou {@code null} se o header
	 * {@code Authorization} não existir ou não tiver esse prefixo exato.
	 */
	static String extrairToken(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		return header != null && header.startsWith(PREFIXO_BEARER)
				? header.substring(PREFIXO_BEARER.length())
				: null;
	}

}

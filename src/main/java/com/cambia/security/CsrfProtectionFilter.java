package com.cambia.security;

import java.io.IOException;
import java.util.Set;

import com.cambia.auth.NomesCookieAuth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Achado de revisão de segurança (Fase 3B): reintroduz proteção CSRF, necessária a partir
 * do momento em que a sessão passou a viver também num cookie (credencial ambiente — o
 * navegador manda sozinho, sem o front-end pedir). Padrão "dublê de cookie": um cookie
 * {@code XSRF-TOKEN} legível por JavaScript precisa ser ecoado de volta no header
 * {@code X-XSRF-TOKEN} em toda requisição que muda estado — um site de terceiros não
 * consegue ler o cookie desta origem, então não consegue montar esse header sozinho.
 *
 * <p>Só exige isso quando a requisição realmente depende do cookie de sessão pra
 * autenticar: se já veio um header {@code Authorization: Bearer ...} (esquema de dev
 * local, sem o reverse-proxy HTTPS), o CSRF não se aplica — um site de terceiros não tem
 * como conhecer/forjar esse header, esse fluxo já é imune por natureza. E se não existe
 * cookie de sessão nenhum na requisição (endpoints de pré-login, como
 * {@code /auth/magic-link} e {@code /auth/bootstrap-admin} — ninguém autenticou ainda, não
 * há credencial ambiente nenhuma sendo explorada), também não se aplica. Isso também
 * significa que nenhum teste automatizado existente (todos usam o header) precisou mudar
 * por causa disto.
 *
 * <p>Achado de revisão de segurança ("olhar de hacker ético" — não explorável hoje, mas
 * frágil): a checagem do header precisa usar exatamente o mesmo critério que
 * {@link SessaoAuthenticationFilter} usa pra decidir se autentica por ele — ver
 * {@link AutorizacaoHeader}. Checar só "existe o header" (sem exigir o prefixo
 * {@code "Bearer "}) isentava CSRF mesmo quando esse header não seria de fato usado pra
 * autenticar, deixando a decisão real de autenticação cair pro cookie de sessão sem o
 * CSRF correspondente ter sido exigido.
 */
@Component
class CsrfProtectionFilter extends OncePerRequestFilter {

	private static final Set<String> METODOS_SEGUROS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (precisaValidarCsrf(request) && !tokenCsrfValido(request)) {
			response.setStatus(HttpServletResponse.SC_FORBIDDEN);
			response.setCharacterEncoding("UTF-8");
			response.setContentType("application/json");
			response.getWriter().write("{\"detail\":\"Token CSRF ausente ou inválido. Recarregue a página e tente de novo.\"}");
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean precisaValidarCsrf(HttpServletRequest request) {
		if (METODOS_SEGUROS.contains(request.getMethod()) || AutorizacaoHeader.extrairToken(request) != null) {
			return false;
		}
		// Só passa daqui se depende de fato do cookie de sessão pra autenticar — sem esse
		// cookie (endpoints de pré-login, ou uma requisição sem credencial nenhuma que vai
		// tomar 401 de qualquer jeito) não há credencial ambiente relevante pro CSRF.
		return valorDoCookie(request, NomesCookieAuth.SESSAO) != null;
	}

	private boolean tokenCsrfValido(HttpServletRequest request) {
		String doCookie = valorDoCookie(request, NomesCookieAuth.CSRF);
		String doHeader = request.getHeader("X-XSRF-TOKEN");
		return doCookie != null && doCookie.equals(doHeader);
	}

	private String valorDoCookie(HttpServletRequest request, String nome) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (nome.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

}

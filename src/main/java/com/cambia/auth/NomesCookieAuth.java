package com.cambia.auth;

/**
 * Nomes dos cookies usados pela sessão em produção (Fase 3B da revisão de segurança) —
 * compartilhado entre {@code com.cambia.auth} (que os grava/limpa) e
 * {@code com.cambia.security} (que os lê). Ver decisoes.md.
 */
public final class NomesCookieAuth {

	/** httpOnly — o token de sessão em si, nunca legível por JavaScript. */
	public static final String SESSAO = "cambia_sessao";

	/** Não-httpOnly — só serve pro dublê-cookie de proteção CSRF (ver CsrfProtectionFilter). */
	public static final String CSRF = "XSRF-TOKEN";

	/**
	 * httpOnly — nonce que amarra um pedido de link mágico ao navegador que pediu,
	 * fechando o "login CSRF" em {@code /auth/verify}: sem isso, um atacante conseguia
	 * pedir seu próprio link e induzir a vítima a completá-lo, autenticando a vítima na
	 * conta do atacante. Só existe entre {@code POST /auth/magic-link} e
	 * {@code GET /auth/verify} (Path=/auth, expira com o próprio link). Ver AuthController.
	 */
	public static final String VINCULO_LOGIN = "cambia_vinculo_login";

	private NomesCookieAuth() {
	}

}

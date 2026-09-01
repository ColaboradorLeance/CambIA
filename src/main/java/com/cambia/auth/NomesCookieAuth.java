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

	private NomesCookieAuth() {
	}

}

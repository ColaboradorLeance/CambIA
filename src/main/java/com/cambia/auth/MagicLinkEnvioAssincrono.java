package com.cambia.auth;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Achado de revisão de segurança: {@code /auth/magic-link} respondia mais rápido pra
 * e-mails não cadastrados (não faz nada) do que pra e-mails cadastrados (grava o token e
 * ainda espera o envio do e-mail, que sobre SMTP de verdade pode levar bem mais tempo) —
 * um sinal de tempo observável que permite adivinhar quais e-mails existem no sistema.
 * Rodar o envio em segundo plano faz a resposta ao navegador não esperar mais pela parte
 * lenta (o SMTP), reduzindo a diferença a só a gravação do token no banco — uma diferença
 * bem menor e mais difícil de medir de forma confiável de fora.
 */
@Component
class MagicLinkEnvioAssincrono {

	private final MagicLinkSender sender;

	MagicLinkEnvioAssincrono(MagicLinkSender sender) {
		this.sender = sender;
	}

	@Async
	void enviar(String email, String token) {
		sender.enviar(email, token);
	}

}

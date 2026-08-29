package com.cambia.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Usado enquanto o envio real por SMTP não está habilitado (cambia.mail.habilitado=true)
 * — registra o link no log em vez de enviar e-mail de verdade. É o padrão em
 * desenvolvimento (não exige servidor de e-mail configurado); ver {@link SmtpMagicLinkSender}
 * para o envio real.
 */
@Component
@ConditionalOnProperty(prefix = "cambia.mail", name = "habilitado", havingValue = "false", matchIfMissing = true)
class LoggingMagicLinkSender implements MagicLinkSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingMagicLinkSender.class);

	@Override
	public void enviar(String email, String token) {
		log.info("Link mágico para {}: /auth/verify?token={}", email, token);
	}

}

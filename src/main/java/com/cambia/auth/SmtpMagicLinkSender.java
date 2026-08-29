package com.cambia.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Envio real por SMTP (host/porta/credenciais configurados via variáveis de ambiente
 * padrão do Spring — SPRING_MAIL_HOST, SPRING_MAIL_PORT, SPRING_MAIL_USERNAME,
 * SPRING_MAIL_PASSWORD). Não é específico de nenhum provedor: funciona com qualquer
 * servidor SMTP com autenticação (inclusive o relay SMTP do Microsoft 365 / Exchange
 * Online hospedado no Azure, que é o que a empresa vai usar).
 *
 * Ativado só quando cambia.mail.habilitado=true (variável CAMBIA_MAIL_HABILITADO) —
 * caso contrário o sistema continua usando {@link LoggingMagicLinkSender}, sem exigir
 * um servidor de e-mail configurado em desenvolvimento.
 */
@Component
@ConditionalOnProperty(prefix = "cambia.mail", name = "habilitado", havingValue = "true")
class SmtpMagicLinkSender implements MagicLinkSender {

	private final JavaMailSender mailSender;
	private final String remetente;

	SmtpMagicLinkSender(JavaMailSender mailSender, CambiaMailProperties mailProperties) {
		this.mailSender = mailSender;
		this.remetente = mailProperties.remetente();
	}

	@Override
	public void enviar(String email, String token) {
		SimpleMailMessage mensagem = new SimpleMailMessage();
		mensagem.setFrom(remetente);
		mensagem.setTo(email);
		mensagem.setSubject("Seu link de acesso ao CambIA");
		mensagem.setText("""
				Olá!

				Use o código abaixo para entrar no CambIA:

				%s

				Cole esse código na tela de login. Ele expira em 15 minutos.

				Se você não solicitou este acesso, ignore este e-mail.
				""".formatted(token));
		mailSender.send(mensagem);
	}

}

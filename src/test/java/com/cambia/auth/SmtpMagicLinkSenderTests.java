package com.cambia.auth;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpMagicLinkSenderTests {

	@Test
	void enviaEmailComRemetenteDestinatarioEToken() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		CambiaMailProperties propriedades = new CambiaMailProperties(true, "no-reply@cambia.com.br");
		SmtpMagicLinkSender sender = new SmtpMagicLinkSender(mailSender, propriedades);

		sender.enviar("usuario@empresa.com.br", "abc123");

		ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(mailSender).send(captor.capture());

		SimpleMailMessage mensagem = captor.getValue();
		assertEquals("no-reply@cambia.com.br", mensagem.getFrom());
		assertEquals("usuario@empresa.com.br", mensagem.getTo()[0]);
		assertTrue(mensagem.getSubject().contains("CambIA"));
		assertTrue(mensagem.getText().contains("abc123"));
	}

}

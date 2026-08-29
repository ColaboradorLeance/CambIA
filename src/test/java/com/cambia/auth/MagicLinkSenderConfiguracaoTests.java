package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MagicLinkSenderConfiguracaoTests {

	@Autowired
	private MagicLinkSender magicLinkSender;

	@DynamicPropertySource
	static void configurarPropriedades(DynamicPropertyRegistry registry) {
		registry.add("cambia.mail.habilitado", () -> "true");
		registry.add("cambia.mail.remetente", () -> "no-reply@cambia.com.br");
		registry.add("spring.mail.host", () -> "localhost");
		registry.add("spring.mail.port", () -> "2525");
	}

	@Test
	void quandoHabilitadoUsaEnvioPorSmtp() {
		assertInstanceOf(SmtpMagicLinkSender.class, magicLinkSender);
	}

}

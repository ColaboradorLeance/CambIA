package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;
import java.util.Properties;

import com.cambia.auth.CambiaMailProperties;

import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FechamentoEmailServiceTests {

	private JavaMailSender mailSenderComMimeMessageReal() {
		JavaMailSender mailSender = mock(JavaMailSender.class);
		Session session = Session.getDefaultInstance(new Properties());
		when(mailSender.createMimeMessage()).thenAnswer(invocacao -> new MimeMessage(session));
		return mailSender;
	}

	@Test
	void enviaUmEmailPorDestinatarioComOsDoisAnexos() throws Exception {
		JavaMailSender mailSender = mailSenderComMimeMessageReal();
		CambiaMailProperties propriedades = new CambiaMailProperties(true, "no-reply@cambia.com.br");
		FechamentoEmailService service = new FechamentoEmailService(mailSender, propriedades);

		service.enviar(LocalDate.of(2026, 7, 2), "PDF-CONTEUDO".getBytes(), "XLSX-CONTEUDO".getBytes(),
				List.of("financeiro@empresa.com.br", "socio@empresa.com.br"));

		ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(mailSender, times(2)).send(captor.capture());

		List<MimeMessage> enviados = captor.getAllValues();
		assertEquals("financeiro@empresa.com.br", enviados.get(0).getAllRecipients()[0].toString());
		assertEquals("socio@empresa.com.br", enviados.get(1).getAllRecipients()[0].toString());
		assertTrue(enviados.get(0).getSubject().contains("Fechamento Diário"));
		// no assunto/corpo a data aparece em dd/mm/aaaa (não no formato cru da API)
		assertTrue(enviados.get(0).getSubject().contains("02/07/2026"));

		assertInstanceOf(Multipart.class, enviados.get(0).getContent());
		Multipart multipart = (Multipart) enviados.get(0).getContent();
		assertEquals(3, multipart.getCount()); // corpo do texto + PDF + Excel
		// nome do arquivo continua em aaaa-mm-dd (não pode ter "/" num nome de arquivo)
		assertEquals("fechamento-2026-07-02.pdf", multipart.getBodyPart(1).getFileName());
		assertEquals("fechamento-2026-07-02.xlsx", multipart.getBodyPart(2).getFileName());
	}

	@Test
	void continuaEnviandoParaOsOutrosDestinatariosSeUmFalhar() throws Exception {
		JavaMailSender mailSender = mailSenderComMimeMessageReal();
		doThrow(new MailSendException("falha simulada")).doNothing().when(mailSender).send(any(MimeMessage.class));

		CambiaMailProperties propriedades = new CambiaMailProperties(true, "no-reply@cambia.com.br");
		FechamentoEmailService service = new FechamentoEmailService(mailSender, propriedades);

		service.enviar(LocalDate.of(2026, 7, 2), new byte[] { 1 }, new byte[] { 2 },
				List.of("destinatario-com-erro@empresa.com.br", "destinatario-ok@empresa.com.br"));

		verify(mailSender, times(2)).send(any(MimeMessage.class));
	}

}

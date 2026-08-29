package com.cambia.operacao;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.cambia.auth.CambiaMailProperties;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Envia o PDF e o Excel do fechamento diário, anexados na mesma mensagem, a uma lista
 * de destinatários (Usuários cadastrados como destinatários do fechamento). Reaproveita
 * a mesma configuração de SMTP do link mágico ({@code cambia.mail.habilitado}) — só
 * existe quando o envio de e-mail está ligado, já que depende do mesmo servidor.
 */
@Service
@ConditionalOnProperty(prefix = "cambia.mail", name = "habilitado", havingValue = "true")
class FechamentoEmailService {

	private static final Logger LOG = LoggerFactory.getLogger(FechamentoEmailService.class);
	private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	private final JavaMailSender mailSender;
	private final String remetente;

	FechamentoEmailService(JavaMailSender mailSender, CambiaMailProperties mailProperties) {
		this.mailSender = mailSender;
		this.remetente = mailProperties.remetente();
	}

	void enviar(LocalDate data, byte[] pdf, byte[] excel, List<String> destinatarios) {
		for (String destinatario : destinatarios) {
			try {
				enviarPara(data, pdf, excel, destinatario);
			} catch (MessagingException | RuntimeException e) {
				LOG.warn("Falha ao enviar o fechamento de {} para {}: {}", data, destinatario, e.getMessage());
			}
		}
	}

	private void enviarPara(LocalDate data, byte[] pdf, byte[] excel, String destinatario) throws MessagingException {
		String dataFormatada = data.format(FORMATO_DATA);

		MimeMessage mensagem = mailSender.createMimeMessage();
		MimeMessageHelper helper = new MimeMessageHelper(mensagem, true);
		helper.setFrom(remetente);
		helper.setTo(destinatario);
		helper.setSubject("Fechamento Diário — " + dataFormatada);
		helper.setText("""
				Olá!

				Segue em anexo o fechamento diário de %s, em PDF e Excel.

				Este e-mail é gerado automaticamente pelo CambIA.
				""".formatted(dataFormatada));
		// nome do arquivo continua em aaaa-mm-dd (não pode ter "/" e ordena bem por nome)
		helper.addAttachment("fechamento-" + data + ".pdf", new ByteArrayResource(pdf));
		helper.addAttachment("fechamento-" + data + ".xlsx", new ByteArrayResource(excel));
		mailSender.send(mensagem);
	}

}

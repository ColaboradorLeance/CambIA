package com.cambia.operacao;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
class FechamentoAgendadorService {

	private final ConfiguracaoFechamentoRepository configuracaoRepository;
	private final FechamentoGeradoRepository fechamentoGeradoRepository;
	private final FechamentoDestinatarioRepository destinatarioRepository;
	private final UsuarioRepository usuarioRepository;
	private final FechamentoService fechamentoService;
	private final Optional<FechamentoEmailService> emailService;

	FechamentoAgendadorService(ConfiguracaoFechamentoRepository configuracaoRepository,
			FechamentoGeradoRepository fechamentoGeradoRepository,
			FechamentoDestinatarioRepository destinatarioRepository, UsuarioRepository usuarioRepository,
			FechamentoService fechamentoService, Optional<FechamentoEmailService> emailService) {
		this.configuracaoRepository = configuracaoRepository;
		this.fechamentoGeradoRepository = fechamentoGeradoRepository;
		this.destinatarioRepository = destinatarioRepository;
		this.usuarioRepository = usuarioRepository;
		this.fechamentoService = fechamentoService;
		this.emailService = emailService;
	}

	@Scheduled(cron = "0 * * * * *")
	void verificarEGerarSeNecessario() {
		verificarEGerarSeNecessario(LocalDateTime.now());
	}

	void verificarEGerarSeNecessario(LocalDateTime agora) {
		List<ConfiguracaoFechamento> configuracoes = configuracaoRepository.findAll();
		if (configuracoes.isEmpty()) {
			return;
		}
		LocalTime horaConfigurada = configuracoes.get(0).getHoraExecucao();
		LocalTime horaAtual = agora.toLocalTime().withSecond(0).withNano(0);
		if (!horaAtual.equals(horaConfigurada.withSecond(0).withNano(0))) {
			return;
		}

		LocalDate hoje = agora.toLocalDate();
		if (fechamentoGeradoRepository.existsByData(hoje)) {
			return;
		}

		gerarEArmazenar(hoje, agora);
	}

	void gerarEArmazenar(LocalDate data, LocalDateTime agora) {
		FechamentoResponse fechamento = fechamentoService.calcular(data);
		Instant geradoEm = agora.atZone(java.time.ZoneId.systemDefault()).toInstant();

		byte[] pdf = FechamentoPdfExporter.gerar(fechamento);
		fechamentoGeradoRepository.save(new FechamentoGerado(data, "PDF", "fechamento-" + data + ".pdf", pdf, geradoEm));

		byte[] excel = FechamentoExcelExporter.gerar(fechamento);
		fechamentoGeradoRepository
				.save(new FechamentoGerado(data, "XLSX", "fechamento-" + data + ".xlsx", excel, geradoEm));

		enviarPorEmailSeConfigurado(data, pdf, excel);
	}

	private void enviarPorEmailSeConfigurado(LocalDate data, byte[] pdf, byte[] excel) {
		if (emailService.isEmpty()) {
			return;
		}
		List<String> emails = destinatarioRepository.findAll().stream()
				.map(FechamentoDestinatario::getUsuarioId)
				.map(usuarioRepository::findById)
				.flatMap(Optional::stream)
				.map(Usuario::getEmail)
				.toList();
		if (emails.isEmpty()) {
			return;
		}
		emailService.get().enviar(data, pdf, excel, emails);
	}

}

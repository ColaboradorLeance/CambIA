package com.cambia.operacao;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import com.cambia.usuario.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class FechamentoConfiguracaoService {

	private final ConfiguracaoFechamentoRepository configuracaoRepository;
	private final FechamentoGeradoRepository fechamentoGeradoRepository;
	private final FechamentoDestinatarioRepository destinatarioRepository;
	private final UsuarioRepository usuarioRepository;

	FechamentoConfiguracaoService(ConfiguracaoFechamentoRepository configuracaoRepository,
			FechamentoGeradoRepository fechamentoGeradoRepository,
			FechamentoDestinatarioRepository destinatarioRepository, UsuarioRepository usuarioRepository) {
		this.configuracaoRepository = configuracaoRepository;
		this.fechamentoGeradoRepository = fechamentoGeradoRepository;
		this.destinatarioRepository = destinatarioRepository;
		this.usuarioRepository = usuarioRepository;
	}

	ConfiguracaoFechamentoResponse obter() {
		LocalTime hora = configuracaoRepository.findAll().stream()
				.findFirst()
				.map(ConfiguracaoFechamento::getHoraExecucao)
				.orElse(null);
		return new ConfiguracaoFechamentoResponse(hora);
	}

	ConfiguracaoFechamentoResponse definir(LocalTime horaExecucao) {
		ConfiguracaoFechamento configuracao = configuracaoRepository.findAll().stream()
				.findFirst()
				.orElseGet(() -> new ConfiguracaoFechamento(horaExecucao));
		configuracao.atualizar(horaExecucao);
		configuracaoRepository.save(configuracao);
		return new ConfiguracaoFechamentoResponse(horaExecucao);
	}

	List<FechamentoHistoricoItem> listarHistorico() {
		return fechamentoGeradoRepository.findAllByOrderByGeradoEmDesc().stream()
				.map(FechamentoHistoricoItem::from)
				.toList();
	}

	FechamentoGerado buscarGerado(Long id) {
		return fechamentoGeradoRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fechamento gerado não encontrado"));
	}

	List<DestinatarioFechamentoResponse> listarDestinatarios() {
		return destinatarioRepository.findAll().stream()
				.map(FechamentoDestinatario::getUsuarioId)
				.map(usuarioRepository::findById)
				.flatMap(Optional::stream)
				.map(u -> new DestinatarioFechamentoResponse(u.getId(), u.getNome(), u.getEmail()))
				.toList();
	}

	List<DestinatarioFechamentoResponse> definirDestinatarios(List<Long> usuarioIds) {
		List<Long> distintos = usuarioIds.stream().distinct().toList();
		for (Long usuarioId : distintos) {
			if (!usuarioRepository.existsById(usuarioId)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Usuário " + usuarioId + " não existe");
			}
		}
		destinatarioRepository.deleteAll();
		destinatarioRepository.flush();
		destinatarioRepository.saveAll(distintos.stream().map(FechamentoDestinatario::new).toList());
		return listarDestinatarios();
	}

}

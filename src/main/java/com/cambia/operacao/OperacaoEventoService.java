package com.cambia.operacao;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.stereotype.Service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
class OperacaoEventoService {

	private final OperacaoEventoRepository repository;
	private final OperacaoRepository operacaoRepository;
	private final UsuarioRepository usuarioRepository;
	private final ObjectMapper objectMapper;

	OperacaoEventoService(OperacaoEventoRepository repository, OperacaoRepository operacaoRepository,
			UsuarioRepository usuarioRepository, ObjectMapper objectMapper) {
		this.repository = repository;
		this.operacaoRepository = operacaoRepository;
		this.usuarioRepository = usuarioRepository;
		this.objectMapper = objectMapper;
	}

	void registrar(TipoEventoOperacao tipo, Long operacaoId, Long usuarioId, Instant agora,
			OperacaoSnapshot dadosAnteriores) {
		String json = dadosAnteriores == null ? null : escrever(dadosAnteriores);
		repository.save(new OperacaoEvento(operacaoId, tipo, usuarioId, agora, json));
	}

	private String escrever(OperacaoSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(snapshot);
		} catch (JacksonException e) {
			throw new IllegalStateException("Falha ao serializar snapshot da operação", e);
		}
	}

	private OperacaoSnapshot ler(String json) {
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json, OperacaoSnapshot.class);
		} catch (JacksonException e) {
			throw new IllegalStateException("Falha ao ler snapshot da operação", e);
		}
	}

	List<OperacaoEventoResponse> listarHistorico() {
		List<OperacaoEvento> eventos = repository.findAllByOrderByCriadoEmDescIdDesc();

		Map<Long, String> idTradePorOperacao = operacaoRepository.findAll().stream()
				.collect(java.util.stream.Collectors.toMap(Operacao::getId, Operacao::getIdTrade));

		return eventos.stream()
				.map(evento -> new OperacaoEventoResponse(
						evento.getId(),
						evento.getOperacaoId(),
						idTradePorOperacao.getOrDefault(evento.getOperacaoId(), "#" + evento.getOperacaoId()),
						evento.getTipo(),
						usuarioRepository.findById(evento.getUsuarioId()).map(Usuario::getNome)
								.orElse("Usuário #" + evento.getUsuarioId()),
						evento.getCriadoEm(),
						ler(evento.getDadosAnteriores())))
				.toList();
	}

}

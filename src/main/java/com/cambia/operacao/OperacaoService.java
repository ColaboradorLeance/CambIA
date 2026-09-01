package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.cambia.banco.BancoRepository;
import com.cambia.cliente.ClienteRepository;
import com.cambia.usuario.UsuarioRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class OperacaoService {

	private final OperacaoRepository repository;
	private final ClienteRepository clienteRepository;
	private final BancoRepository bancoRepository;
	private final UsuarioRepository usuarioRepository;
	private final OperacaoEventoService eventoService;

	OperacaoService(OperacaoRepository repository, ClienteRepository clienteRepository,
			BancoRepository bancoRepository, UsuarioRepository usuarioRepository,
			OperacaoEventoService eventoService) {
		this.repository = repository;
		this.clienteRepository = clienteRepository;
		this.bancoRepository = bancoRepository;
		this.usuarioRepository = usuarioRepository;
		this.eventoService = eventoService;
	}

	Operacao criar(OperacaoRequest request, Long criadoPorUsuarioId) {
		if (!clienteRepository.existsById(request.clienteId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente informado não existe");
		}
		if (!bancoRepository.existsById(request.bancoId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banco informado não existe");
		}

		String idTrade = gerarIdTrade(request.data().getYear());
		Instant agora = Instant.now();

		Operacao operacao = new Operacao(idTrade, request.data(), request.codigoBanco(), request.clienteId(),
				request.bancoId(), request.cv(), request.prCrVir(), request.moeda(), request.valorMe(),
				request.spotAsset(), request.nivelamento(), request.taxaFinal(), criadoPorUsuarioId, agora);
		operacao = repository.save(operacao);

		eventoService.registrar(TipoEventoOperacao.CRIADA, operacao.getId(), criadoPorUsuarioId, agora,
				OperacaoSnapshot.de(operacao, nomeCliente(operacao.getClienteId()), nomeBanco(operacao.getBancoId())));
		return operacao;
	}

	Operacao editar(Long id, OperacaoRequest request, Long usuarioId) {
		Operacao operacao = buscar(id);
		if (operacao.getStatus() != StatusOperacao.ANDAMENTO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Só é possível editar ordens em andamento");
		}
		if (!clienteRepository.existsById(request.clienteId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cliente informado não existe");
		}
		if (!bancoRepository.existsById(request.bancoId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Banco informado não existe");
		}

		OperacaoSnapshot dadosAnteriores = OperacaoSnapshot.de(operacao, nomeCliente(operacao.getClienteId()),
				nomeBanco(operacao.getBancoId()));
		operacao.editar(request.codigoBanco(), request.clienteId(), request.bancoId(), request.cv(),
				request.prCrVir(), request.moeda(), request.valorMe(), request.spotAsset(), request.nivelamento(),
				request.taxaFinal());
		operacao = repository.save(operacao);

		eventoService.registrar(TipoEventoOperacao.EDITADA, operacao.getId(), usuarioId, Instant.now(),
				dadosAnteriores);
		return operacao;
	}

	private String gerarIdTrade(int ano) {
		String prefixo = ano + "-";
		long proximoSequencial = repository.countByIdTradeStartingWith(prefixo) + 1;
		return prefixo + "%06d".formatted(proximoSequencial);
	}

	Operacao buscar(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Operação não encontrada"));
	}

	List<Operacao> listar() {
		return repository.findAll();
	}

	List<Operacao> relatorioOperacoes(Periodo periodo, Long clienteId, Long bancoId, String moeda, String cv,
			StatusOperacao status, Long criadoPorUsuarioId, Long completadoPorUsuarioId, LocalDate hoje) {
		IntervaloDatas intervalo = PeriodoResolver.resolver(periodo, hoje);
		return repository.buscarFiltrado(intervalo.inicio(), intervalo.fim(), clienteId, bancoId, moeda, cv, status,
				criadoPorUsuarioId, completadoPorUsuarioId);
	}

	Operacao atualizarStatus(Long id, StatusOperacao novoStatus, Long usuarioId) {
		Operacao operacao = buscar(id);
		// Confirmado e Cancelado são definitivos: só se sai de "em andamento" pra um dos dois,
		// nunca o contrário e nunca de um pro outro (pedido do usuário).
		if (operacao.getStatus() != StatusOperacao.ANDAMENTO) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Só é possível confirmar ou cancelar ordens em andamento");
		}
		if (novoStatus != StatusOperacao.CONFIRMADO && novoStatus != StatusOperacao.CANCELADO) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Uma ordem em andamento só pode ser confirmada ou cancelada");
		}

		Instant agora = Instant.now();
		operacao.atualizarStatus(novoStatus, usuarioId, agora);
		operacao = repository.save(operacao);

		TipoEventoOperacao tipoEvento = novoStatus == StatusOperacao.CONFIRMADO ? TipoEventoOperacao.CONFIRMADA
				: TipoEventoOperacao.CANCELADA;
		eventoService.registrar(tipoEvento, operacao.getId(), usuarioId, agora, null);
		return operacao;
	}

	List<OperacaoEventoResponse> historico() {
		return eventoService.listarHistorico();
	}

	OperacaoResponse toResponse(Operacao operacao) {
		String criadoPorNome = usuarioRepository.findById(operacao.getCriadoPorUsuarioId())
				.map(u -> u.getNome())
				.orElse(null);
		String completadoPorNome = operacao.getCompletadoPorUsuarioId() == null ? null
				: usuarioRepository.findById(operacao.getCompletadoPorUsuarioId())
						.map(u -> u.getNome())
						.orElse(null);
		String clienteNome = nomeCliente(operacao.getClienteId());
		String clienteDocumento = documentoCliente(operacao.getClienteId());
		String bancoNome = nomeBanco(operacao.getBancoId());

		if (operacao.getStatus() != StatusOperacao.CONFIRMADO) {
			return OperacaoResponse.from(operacao, new ValoresCalculados(null, null, null, null, null), criadoPorNome,
					completadoPorNome, clienteNome, clienteDocumento, bancoNome);
		}
		String formulaComissao = bancoRepository.findCalculoFormulaById(operacao.getBancoId())
				.orElse(null);
		BigDecimal taxaRebate = bancoRepository.findTaxaRebateById(operacao.getBancoId())
				.orElse(null);
		ValoresCalculados valores = OperacaoCalculo.calcular(operacao.getValorMe(), operacao.getNivelamento(),
				operacao.getTaxaFinal(), operacao.getCv(), formulaComissao, taxaRebate);
		return OperacaoResponse.from(operacao, valores, criadoPorNome, completadoPorNome, clienteNome,
				clienteDocumento, bancoNome);
	}

	private String nomeCliente(Long clienteId) {
		return clienteRepository.findNomeById(clienteId).orElse(null);
	}

	private String documentoCliente(Long clienteId) {
		return clienteRepository.findDocumentoById(clienteId).orElse(null);
	}

	private String nomeBanco(Long bancoId) {
		return bancoRepository.findNomeById(bancoId).orElse(null);
	}

}

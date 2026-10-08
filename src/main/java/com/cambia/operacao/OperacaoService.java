package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.cambia.banco.BancoRepository;
import com.cambia.banco.BancoResumo;
import com.cambia.cliente.ClienteRepository;
import com.cambia.cliente.ClienteResumo;
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
		String prCrVir = validarPrCrVir(request.prCrVir());
		validarSpreadEmissao(prCrVir, request.spreadEmissao());
		String fundo = validarFundo(prCrVir, request.fundo());
		String codigoOperacao = validarCodigoOperacao(prCrVir, request.codigoOperacao());
		validarSpotAsset(prCrVir, request.spotAsset());

		String idTrade = gerarIdTrade(request.data().getYear());
		Instant agora = Instant.now();

		Operacao operacao = new Operacao(idTrade, request.data(), request.codigoBanco(), codigoOperacao,
				request.clienteId(), request.bancoId(), request.cv(), prCrVir, fundo,
				request.spreadEmissao(), request.moeda(), request.valorMe(), request.spotAsset(),
				request.nivelamento(), request.taxaFinal(), criadoPorUsuarioId, agora);
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
		String prCrVir = validarPrCrVir(request.prCrVir());
		validarSpreadEmissao(prCrVir, request.spreadEmissao());
		String fundo = validarFundo(prCrVir, request.fundo());
		String codigoOperacao = validarCodigoOperacao(prCrVir, request.codigoOperacao());
		validarSpotAsset(prCrVir, request.spotAsset());

		OperacaoSnapshot dadosAnteriores = OperacaoSnapshot.de(operacao, nomeCliente(operacao.getClienteId()),
				nomeBanco(operacao.getBancoId()));
		operacao.editar(request.codigoBanco(), codigoOperacao, request.clienteId(), request.bancoId(), request.cv(),
				prCrVir, fundo, request.spreadEmissao(), request.moeda(), request.valorMe(),
				request.spotAsset(), request.nivelamento(), request.taxaFinal());
		operacao = repository.save(operacao);

		eventoService.registrar(TipoEventoOperacao.EDITADA, operacao.getId(), usuarioId, Instant.now(),
				dadosAnteriores);
		return operacao;
	}

	// Incremento 77 (pendência #16, achado da revisão de 2026-10-08): PR/CR/VIR deixou de
	// ser texto livre — domínio fechado em Pronto/Credito/Virtual. Variações de caixa e
	// acento são aceitas e NORMALIZADAS pro canônico (sem acento, a grafia que todo o
	// resto do sistema já comparava); fora disso, 400. Motivo: "Crédito" com acento
	// passava por fora das obrigatoriedades de Crédito (Código da operação/Spot Asset)
	// e calculava Custo 0. As demais validações recebem o valor JÁ normalizado.
	private String validarPrCrVir(String prCrVir) {
		if (prCrVir != null) {
			String semAcento = java.text.Normalizer.normalize(prCrVir, java.text.Normalizer.Form.NFD)
					.replaceAll("\\p{M}", "");
			for (String canonico : List.of("Pronto", "Credito", "Virtual")) {
				if (canonico.equalsIgnoreCase(semAcento)) {
					return canonico;
				}
			}
		}
		throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
				"Tipo da ordem (PR/CR/VIR) deve ser Pronto, Crédito ou Virtual");
	}

	// Achado de negócio (Incremento 56): Spread emissão não é calculado — vem no próprio
	// request, e precisa ser consistente com o tipo da ordem (PR/CR/VIR): "NA" quando é
	// "Pronto" (único tipo que a tela de Registrar Operação consegue criar); um número
	// quando é qualquer outro tipo (Crédito/Virtual, só alcançáveis via API — a tela não
	// oferece essas opções). Rejeitado com 400 quando a combinação não bate, pra não
	// deixar dado inconsistente entrar no banco.
	private void validarSpreadEmissao(String prCrVir, String spreadEmissao) {
		boolean pronto = "Pronto".equals(prCrVir); // prCrVir chega canônico (validarPrCrVir)
		boolean valorNA = "NA".equalsIgnoreCase(spreadEmissao);
		if (pronto && !valorNA) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Spread emissão deve ser \"NA\" quando o tipo da ordem é Pronto");
		}
		if (!pronto) {
			if (valorNA) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Spread emissão deve ser um número quando o tipo da ordem não é Pronto");
			}
			try {
				new BigDecimal(spreadEmissao);
			} catch (NumberFormatException e) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Spread emissão precisa ser um número válido quando o tipo da ordem não é Pronto");
			}
		}
	}

	// Incremento 75 (substitui a regra do Incremento 70, que exigia "M" fixo fora de
	// Pronto): Pronto continua exigindo "P"; qualquer outro tipo aceita QUALQUER LETRA,
	// enviada na requisição — e por isso o valor passou a ser persistido (não é mais
	// derivável do tipo). Continua rejeitando com 400 o que não é uma letra única.
	// Incremento 77 (pendência #15): devolve a letra NORMALIZADA pra maiúscula — "z" e
	// "Z" são o mesmo fundo, e os filtros das telas (match exato) não podem vê-los como
	// dois valores distintos. Recebe o prCrVir já canônico (validarPrCrVir).
	private String validarFundo(String prCrVir, String fundo) {
		boolean pronto = "Pronto".equals(prCrVir);
		if (pronto && !"P".equalsIgnoreCase(fundo)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Fundo deve ser \"P\" quando o tipo da ordem é Pronto");
		}
		if (!pronto && (fundo == null || !fundo.matches("[A-Za-z]"))) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Fundo deve ser uma única letra quando o tipo da ordem não é Pronto");
		}
		return fundo.toUpperCase(java.util.Locale.ROOT);
	}

	// Incremento 71: Código da operação é digitado na criação/edição — só números
	// (guardado como texto, preservando zeros à esquerda). Obrigatório quando o tipo da
	// ordem (PR/CR/VIR) é "Crédito"; opcional nos demais — pedido do usuário ("código da
	// operação = tipo crédito"). Mesmo padrão de validação condicional do Spread emissão
	// e do Fundo acima. Em branco conta como ausente; devolve o valor normalizado (sem
	// espaços nas pontas, ou null).
	private String validarCodigoOperacao(String prCrVir, String codigoOperacao) {
		String valor = codigoOperacao == null || codigoOperacao.isBlank() ? null : codigoOperacao.trim();
		if (valor == null) {
			if ("Credito".equals(prCrVir)) { // canônico (validarPrCrVir)
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Código da operação é obrigatório quando o tipo da ordem é Crédito");
			}
			return null;
		}
		if (!valor.matches("\\d+")) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código da operação só aceita números");
		}
		// Limite técnico da coluna (VARCHAR(20), migração V20): sem este teto, um código
		// maior estourava no INSERT e virava 500 em vez de um 400 explicável.
		if (valor.length() > 20) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Código da operação pode ter no máximo 20 dígitos");
		}
		return valor;
	}

	// Incremento 72: Spot Asset saiu da tela de Registrar Operação — entra somente via
	// API (pedido do usuário). Obrigatório só quando o tipo da ordem é "Crédito", único
	// caso em que entra numa fórmula confirmada (Custo = Spot Asset / Taxa Final − 1);
	// opcional nos demais. Positivo/4 casas continuam garantidos pelas anotações do
	// request quando o valor vem preenchido.
	private void validarSpotAsset(String prCrVir, BigDecimal spotAsset) {
		if (spotAsset == null && "Credito".equals(prCrVir)) { // canônico (validarPrCrVir)
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Spot Asset é obrigatório quando o tipo da ordem é Crédito");
		}
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
		return toResponses(List.of(operacao)).get(0);
	}

	/**
	 * Versão pra listas: as listagens mapeavam linha a linha pelo toResponse e repetiam
	 * as mesmas consultas de nome/fórmula pra cada ordem (~6 queries por linha — achado
	 * de eficiência da revisão de 2026-10-08). Aqui cada usuário/cliente/banco DISTINTO
	 * é consultado uma única vez por chamada — memoização local, sem cache global, então
	 * nunca serve dado velho entre requisições.
	 */
	List<OperacaoResponse> toResponses(List<Operacao> operacoes) {
		Map<Long, Optional<String>> nomesUsuario = new HashMap<>();
		Map<Long, Optional<ClienteResumo>> clientes = new HashMap<>();
		Map<Long, Optional<BancoResumo>> bancos = new HashMap<>();

		return operacoes.stream().map(operacao -> {
			String criadoPorNome = nomesUsuario
					.computeIfAbsent(operacao.getCriadoPorUsuarioId(),
							id -> usuarioRepository.findById(id).map(u -> u.getNome()))
					.orElse(null);
			String completadoPorNome = operacao.getCompletadoPorUsuarioId() == null ? null
					: nomesUsuario
							.computeIfAbsent(operacao.getCompletadoPorUsuarioId(),
									id -> usuarioRepository.findById(id).map(u -> u.getNome()))
							.orElse(null);
			Optional<ClienteResumo> cliente = clientes
					.computeIfAbsent(operacao.getClienteId(), clienteRepository::findResumoById);
			Optional<BancoResumo> banco = bancos
					.computeIfAbsent(operacao.getBancoId(), bancoRepository::findResumoById);

			// Incremento 76 (substitui a regra do Incremento 39, que suprimia os valores
			// fora de CONFIRMADO): os valores calculados existem SEMPRE, em qualquer
			// status — pedido do usuário ("calcular e exibir sempre, inclusive em
			// andamento"). Em andamento eles são uma prévia que acompanha as edições;
			// confirmar continua travando a ordem (e com ela os valores).
			ValoresCalculados valores = OperacaoCalculo.calcular(operacao.getValorMe(), operacao.getSpotAsset(),
					operacao.getNivelamento(), operacao.getTaxaFinal(), operacao.getCv(), operacao.getPrCrVir(),
					operacao.getSpreadEmissao(), banco.map(BancoResumo::formula).orElse(null),
					banco.map(BancoResumo::taxaRebate).orElse(null));
			return OperacaoResponse.from(operacao, valores, criadoPorNome, completadoPorNome,
					cliente.map(ClienteResumo::nome).orElse(null), cliente.map(ClienteResumo::documento).orElse(null),
					banco.map(BancoResumo::nome).orElse(null));
		}).toList();
	}

	private String nomeCliente(Long clienteId) {
		return clienteRepository.findNomeById(clienteId).orElse(null);
	}

	private String nomeBanco(Long bancoId) {
		return bancoRepository.findNomeById(bancoId).orElse(null);
	}

}

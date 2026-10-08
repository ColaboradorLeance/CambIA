package com.cambia.operacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.cambia.banco.BancoRepository;
import com.cambia.cliente.ClienteRepository;

import org.springframework.stereotype.Service;

@Service
class FechamentoService {

	private final OperacaoRepository operacaoRepository;
	private final OperacaoService operacaoService;
	private final ClienteRepository clienteRepository;
	private final BancoRepository bancoRepository;

	FechamentoService(OperacaoRepository operacaoRepository, OperacaoService operacaoService,
			ClienteRepository clienteRepository, BancoRepository bancoRepository) {
		this.operacaoRepository = operacaoRepository;
		this.operacaoService = operacaoService;
		this.clienteRepository = clienteRepository;
		this.bancoRepository = bancoRepository;
	}

	FechamentoResponse calcular(LocalDate data) {
		List<OperacaoResponse> respostas = operacoesDoDia(data);

		// Quebras continuam contando só dinheiro confirmado: desde o Incremento 76
		// toResponse calcula em qualquer status, então os calculados das não confirmadas
		// (em andamento/canceladas) são zerados aqui antes de somar — a ordem continua
		// aparecendo na quantidade do agrupamento, como sempre apareceu. Mesmo gate já
		// aplicado nos Rankings (RelatorioRankingService); sem ele, a prévia de uma ordem
		// em andamento (e até de uma cancelada) inflaria R$/comissão do Fechamento.
		List<OperacaoResponse> respostasSoDinheiroConfirmado = respostas.stream()
				.map(r -> r.status() == StatusOperacao.CONFIRMADO ? r : r.semValoresCalculados())
				.toList();

		return new FechamentoResponse(
				data,
				calcularResumo(respostas),
				calcularResultado(respostas),
				calcularQuebras(respostasSoDinheiroConfirmado),
				calcularPosicaoEmAberto());
	}

	ResultadoFinanceiro calcularResultadoDoDia(LocalDate data) {
		return calcularResultado(operacoesDoDia(data));
	}

	private List<OperacaoResponse> operacoesDoDia(LocalDate data) {
		return operacaoService.toResponses(operacaoRepository.findByData(data));
	}

	private ResumoOperacional calcularResumo(List<OperacaoResponse> respostas) {
		Map<String, Integer> porStatus = new LinkedHashMap<>();
		for (OperacaoResponse resposta : respostas) {
			porStatus.merge(resposta.status().name(), 1, Integer::sum);
		}
		return new ResumoOperacional(respostas.size(), porStatus, respostas);
	}

	private ResultadoFinanceiro calcularResultado(List<OperacaoResponse> respostas) {
		List<OperacaoResponse> completas = respostas.stream()
				.filter(r -> r.status() == StatusOperacao.CONFIRMADO)
				.toList();

		Map<String, BigDecimal> volumePorMoeda = new LinkedHashMap<>();
		Map<String, Integer> quantidadePorMoeda = new LinkedHashMap<>();
		for (OperacaoResponse resposta : completas) {
			volumePorMoeda.merge(resposta.moeda(), resposta.valorMe(), BigDecimal::add);
			quantidadePorMoeda.merge(resposta.moeda(), 1, Integer::sum);
		}

		BigDecimal totalReais = somar(completas, OperacaoResponse::reais);
		BigDecimal totalBruto = somar(completas, OperacaoResponse::totalBrutoCambio);
		BigDecimal totalComissao = somar(completas, OperacaoResponse::comissaoLiquida);

		BigDecimal ticketMedio = completas.isEmpty() ? BigDecimal.ZERO.setScale(2)
				: totalReais.divide(BigDecimal.valueOf(completas.size()), 2, RoundingMode.HALF_UP);

		BigDecimal maior = completas.stream().map(OperacaoResponse::reais).filter(Objects::nonNull)
				.max(BigDecimal::compareTo).orElse(BigDecimal.ZERO.setScale(2));
		BigDecimal menor = completas.stream().map(OperacaoResponse::reais).filter(Objects::nonNull)
				.min(BigDecimal::compareTo).orElse(BigDecimal.ZERO.setScale(2));

		return new ResultadoFinanceiro(completas.size(), volumePorMoeda, quantidadePorMoeda, totalReais, totalBruto,
				totalComissao, ticketMedio, maior, menor);
	}

	private BigDecimal somar(List<OperacaoResponse> respostas, Function<OperacaoResponse, BigDecimal> extrator) {
		return respostas.stream().map(extrator).filter(Objects::nonNull)
				.reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}

	QuebrasPorDimensao calcularQuebras(List<OperacaoResponse> respostas) {
		return new QuebrasPorDimensao(
				agrupar(respostas, r -> resolverNomeBanco(r.bancoId())),
				agrupar(respostas, r -> resolverNomeCliente(r.clienteId())),
				agrupar(respostas, OperacaoResponse::moeda),
				agrupar(respostas, OperacaoResponse::cv));
	}

	List<QuebraItem> agrupar(List<OperacaoResponse> respostas,
			Function<OperacaoResponse, String> chaveExtrator) {
		Map<String, List<OperacaoResponse>> porChave = respostas.stream()
				.collect(Collectors.groupingBy(chaveExtrator, LinkedHashMap::new, Collectors.toList()));

		List<QuebraItem> itens = new ArrayList<>();
		for (Map.Entry<String, List<OperacaoResponse>> entrada : porChave.entrySet()) {
			List<OperacaoResponse> grupo = entrada.getValue();
			itens.add(new QuebraItem(entrada.getKey(), grupo.size(), somar(grupo, OperacaoResponse::reais),
					somar(grupo, OperacaoResponse::comissaoLiquida)));
		}
		return itens;
	}

	private String resolverNomeBanco(Long bancoId) {
		return bancoRepository.findNomeById(bancoId).orElse("Banco #" + bancoId);
	}

	private String resolverNomeCliente(Long clienteId) {
		return clienteRepository.findNomeById(clienteId).orElse("Cliente #" + clienteId);
	}

	private PosicaoEmAberto calcularPosicaoEmAberto() {
		List<Operacao> emAndamento = operacaoRepository.findByStatus(StatusOperacao.ANDAMENTO);

		Map<String, BigDecimal> exposicaoPorMoeda = new LinkedHashMap<>();
		for (Operacao operacao : emAndamento) {
			exposicaoPorMoeda.merge(operacao.getMoeda(), operacao.getValorMe(), BigDecimal::add);
		}

		return new PosicaoEmAberto(emAndamento.size(), exposicaoPorMoeda);
	}

}

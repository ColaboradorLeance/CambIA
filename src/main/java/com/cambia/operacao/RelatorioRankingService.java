package com.cambia.operacao;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

@Service
class RelatorioRankingService {

	private final OperacaoRepository operacaoRepository;
	private final OperacaoService operacaoService;
	private final FechamentoService fechamentoService;

	RelatorioRankingService(OperacaoRepository operacaoRepository, OperacaoService operacaoService,
			FechamentoService fechamentoService) {
		this.operacaoRepository = operacaoRepository;
		this.operacaoService = operacaoService;
		this.fechamentoService = fechamentoService;
	}

	RankingsResponse calcular(Periodo periodo, LocalDate hoje) {
		IntervaloDatas intervalo = PeriodoResolver.resolver(periodo, hoje);
		return calcularParaIntervalo(periodo, intervalo);
	}

	/** Rankings de um período com datas escolhidas livremente pelo usuário (fora dos presets). */
	RankingsResponse calcularParaIntervalo(LocalDate inicio, LocalDate fim) {
		return calcularParaIntervalo(null, new IntervaloDatas(inicio, fim));
	}

	private RankingsResponse calcularParaIntervalo(Periodo periodo, IntervaloDatas intervalo) {
		List<Operacao> operacoes = operacaoRepository.buscarFiltrado(intervalo.inicio(), intervalo.fim(), null, null,
				null, null, null, null, null);
		// Rankings continuam contando só dinheiro confirmado (mesma semântica do
		// Fechamento): desde o Incremento 76 toResponse calcula em qualquer status, então
		// os calculados das não confirmadas são zerados aqui — a ordem continua aparecendo
		// no agrupamento (ex: quem criou e ainda não completou figura com comissão 0).
		List<OperacaoResponse> respostas = operacoes.stream()
				.map(operacaoService::toResponse)
				.map(r -> r.status() == StatusOperacao.CONFIRMADO ? r : r.semValoresCalculados())
				.toList();

		QuebrasPorDimensao quebras = fechamentoService.calcularQuebras(respostas);

		List<OperacaoResponse> completadas = respostas.stream()
				.filter(r -> r.completadoPorNome() != null)
				.toList();

		List<QuebraItem> porUsuarioCriador = ordenar(fechamentoService.agrupar(respostas, OperacaoResponse::criadoPorNome));
		List<QuebraItem> porUsuarioCompletador = ordenar(
				fechamentoService.agrupar(completadas, OperacaoResponse::completadoPorNome));

		return new RankingsResponse(periodo, intervalo.inicio(), intervalo.fim(), ordenar(quebras.porCliente()),
				ordenar(quebras.porBanco()), ordenar(quebras.porMoeda()), ordenar(quebras.porTipo()),
				porUsuarioCriador, porUsuarioCompletador);
	}

	private List<QuebraItem> ordenar(List<QuebraItem> itens) {
		return itens.stream()
				.sorted(Comparator.comparing(QuebraItem::totalComissaoLiquida).reversed())
				.toList();
	}

}

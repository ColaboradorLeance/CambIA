package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import com.cambia.exportacao.ArquivoExportado;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Relatórios em JSON (telas) e em arquivo ({@code /pdf} ou {@code /xlsx}, Incremento 82).
 * Cada relatório tem um método privado que valida os parâmetros e calcula — chamado pelos
 * dois caminhos, pra exportação nunca aceitar ou calcular diferente da tela.
 */
@RestController
@RequestMapping("/relatorios")
class RelatorioController {

	private static final String FORMATO = "/{formato:" + ArquivoExportado.FORMATOS + "}";

	private final OperacaoService service;
	private final RelatorioComparativoService comparativoService;
	private final RelatorioRankingService rankingService;
	private final RelatorioPosicaoAbertoService posicaoAbertoService;
	private final RelatorioExportacaoService exportacaoService;

	RelatorioController(OperacaoService service, RelatorioComparativoService comparativoService,
			RelatorioRankingService rankingService, RelatorioPosicaoAbertoService posicaoAbertoService,
			RelatorioExportacaoService exportacaoService) {
		this.service = service;
		this.comparativoService = comparativoService;
		this.rankingService = rankingService;
		this.posicaoAbertoService = posicaoAbertoService;
		this.exportacaoService = exportacaoService;
	}

	// ------------------------------------------------------------------ Posição em aberto

	@GetMapping("/posicao-aberto")
	PosicaoAbertoResponse posicaoAberto() {
		return posicaoAbertoService.calcular(LocalDate.now());
	}

	@GetMapping("/posicao-aberto" + FORMATO)
	ResponseEntity<byte[]> exportarPosicaoAberto(@PathVariable String formato) {
		LocalDate hoje = LocalDate.now();
		return ArquivoExportado.resposta(exportacaoService.posicaoAberto(posicaoAbertoService.calcular(hoje), hoje),
				formato);
	}

	// ------------------------------------------------------------------ Comparativo

	@GetMapping("/comparativo")
	ComparativoResponse comparativo(
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicioA,
			@RequestParam(required = false) LocalDate fimA,
			@RequestParam(required = false) LocalDate inicioB,
			@RequestParam(required = false) LocalDate fimB) {
		return calcularComparativo(periodo, inicioA, fimA, inicioB, fimB);
	}

	@GetMapping("/comparativo" + FORMATO)
	ResponseEntity<byte[]> exportarComparativo(@PathVariable String formato,
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicioA,
			@RequestParam(required = false) LocalDate fimA,
			@RequestParam(required = false) LocalDate inicioB,
			@RequestParam(required = false) LocalDate fimB) {
		return ArquivoExportado.resposta(
				exportacaoService.comparativo(calcularComparativo(periodo, inicioA, fimA, inicioB, fimB)), formato);
	}

	private ComparativoResponse calcularComparativo(Periodo periodo, LocalDate inicioA, LocalDate fimA,
			LocalDate inicioB, LocalDate fimB) {
		if (informouDoisPeriodos(inicioA, fimA, inicioB, fimB)) {
			validarDoisPeriodos(inicioA, fimA, inicioB, fimB);
			return comparativoService.calcularEntreDoisPeriodos(inicioA, fimA, inicioB, fimB);
		}
		if (periodo == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Informe periodo ou os dois períodos (inicioA/fimA/inicioB/fimB)");
		}
		return comparativoService.calcular(periodo, LocalDate.now());
	}

	// ------------------------------------------------------------------ Rankings

	@GetMapping("/rankings")
	RankingsResponse rankings(
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicio,
			@RequestParam(required = false) LocalDate fim) {
		return calcularRankings(periodo, inicio, fim);
	}

	/**
	 * Além de periodo ou inicio/fim (como o JSON), aceita inicioA/fimA/inicioB/fimB: o modo
	 * "dois períodos" da tela busca dois rankings, e o arquivo traz os dois.
	 */
	@GetMapping("/rankings" + FORMATO)
	ResponseEntity<byte[]> exportarRankings(@PathVariable String formato,
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicio,
			@RequestParam(required = false) LocalDate fim,
			@RequestParam(required = false) LocalDate inicioA,
			@RequestParam(required = false) LocalDate fimA,
			@RequestParam(required = false) LocalDate inicioB,
			@RequestParam(required = false) LocalDate fimB) {
		if (informouDoisPeriodos(inicioA, fimA, inicioB, fimB)) {
			validarDoisPeriodos(inicioA, fimA, inicioB, fimB);
			return ArquivoExportado.resposta(exportacaoService.rankingsEntreDoisPeriodos(
					rankingService.calcularParaIntervalo(inicioA, fimA),
					rankingService.calcularParaIntervalo(inicioB, fimB)), formato);
		}
		return ArquivoExportado.resposta(exportacaoService.rankings(calcularRankings(periodo, inicio, fim)), formato);
	}

	private RankingsResponse calcularRankings(Periodo periodo, LocalDate inicio, LocalDate fim) {
		if (inicio != null || fim != null) {
			if (inicio == null || fim == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe inicio e fim juntos");
			}
			if (fim.isBefore(inicio)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fim não pode ser anterior a inicio");
			}
			return rankingService.calcularParaIntervalo(inicio, fim);
		}
		if (periodo == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe periodo ou inicio/fim");
		}
		return rankingService.calcular(periodo, LocalDate.now());
	}

	// ------------------------------------------------------------------ Ordens

	@GetMapping("/operacoes")
	List<OperacaoResponse> operacoes(@RequestParam Periodo periodo, FiltrosRelatorioOrdens filtros) {
		return ordens(periodo, filtros, LocalDate.now());
	}

	@GetMapping("/operacoes" + FORMATO)
	ResponseEntity<byte[]> exportarOperacoes(@PathVariable String formato, @RequestParam Periodo periodo,
			FiltrosRelatorioOrdens filtros) {
		LocalDate hoje = LocalDate.now();
		return ArquivoExportado.resposta(exportacaoService.ordens(ordens(periodo, filtros, hoje),
				PeriodoResolver.resolver(periodo, hoje), filtros), formato);
	}

	private List<OperacaoResponse> ordens(Periodo periodo, FiltrosRelatorioOrdens f, LocalDate hoje) {
		return service.toResponses(service.relatorioOperacoes(periodo, f.clienteId(), f.bancoId(), f.moeda(), f.cv(),
				f.status(), f.criadoPorUsuarioId(), f.completadoPorUsuarioId(), hoje));
	}

	// ------------------------------------------------------------------ comum

	private static boolean informouDoisPeriodos(LocalDate inicioA, LocalDate fimA, LocalDate inicioB, LocalDate fimB) {
		return inicioA != null || fimA != null || inicioB != null || fimB != null;
	}

	private static void validarDoisPeriodos(LocalDate inicioA, LocalDate fimA, LocalDate inicioB, LocalDate fimB) {
		if (inicioA == null || fimA == null || inicioB == null || fimB == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe inicioA, fimA, inicioB e fimB juntos");
		}
		if (fimA.isBefore(inicioA) || fimB.isBefore(inicioB)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"A data fim de um período não pode ser anterior à data início dele");
		}
	}

}

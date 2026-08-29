package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/relatorios")
class RelatorioController {

	private final OperacaoService service;
	private final RelatorioComparativoService comparativoService;
	private final RelatorioRankingService rankingService;
	private final RelatorioPosicaoAbertoService posicaoAbertoService;

	RelatorioController(OperacaoService service, RelatorioComparativoService comparativoService,
			RelatorioRankingService rankingService, RelatorioPosicaoAbertoService posicaoAbertoService) {
		this.service = service;
		this.comparativoService = comparativoService;
		this.rankingService = rankingService;
		this.posicaoAbertoService = posicaoAbertoService;
	}

	@GetMapping("/posicao-aberto")
	PosicaoAbertoResponse posicaoAberto() {
		return posicaoAbertoService.calcular(LocalDate.now());
	}

	@GetMapping("/comparativo")
	ComparativoResponse comparativo(
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicioA,
			@RequestParam(required = false) LocalDate fimA,
			@RequestParam(required = false) LocalDate inicioB,
			@RequestParam(required = false) LocalDate fimB) {
		boolean informouDoisPeriodos = inicioA != null || fimA != null || inicioB != null || fimB != null;
		if (informouDoisPeriodos) {
			if (inicioA == null || fimA == null || inicioB == null || fimB == null) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"Informe inicioA, fimA, inicioB e fimB juntos");
			}
			if (fimA.isBefore(inicioA) || fimB.isBefore(inicioB)) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"A data fim de um período não pode ser anterior à data início dele");
			}
			return comparativoService.calcularEntreDoisPeriodos(inicioA, fimA, inicioB, fimB);
		}
		if (periodo == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"Informe periodo ou os dois períodos (inicioA/fimA/inicioB/fimB)");
		}
		return comparativoService.calcular(periodo, LocalDate.now());
	}

	@GetMapping("/rankings")
	RankingsResponse rankings(
			@RequestParam(required = false) Periodo periodo,
			@RequestParam(required = false) LocalDate inicio,
			@RequestParam(required = false) LocalDate fim) {
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

	@GetMapping("/operacoes")
	List<OperacaoResponse> operacoes(
			@RequestParam Periodo periodo,
			@RequestParam(required = false) Long clienteId,
			@RequestParam(required = false) Long bancoId,
			@RequestParam(required = false) String moeda,
			@RequestParam(required = false) String cv,
			@RequestParam(required = false) StatusOperacao status,
			@RequestParam(required = false) Long criadoPorUsuarioId,
			@RequestParam(required = false) Long completadoPorUsuarioId) {
		return service
				.relatorioOperacoes(periodo, clienteId, bancoId, moeda, cv, status, criadoPorUsuarioId,
						completadoPorUsuarioId, LocalDate.now())
				.stream()
				.map(service::toResponse)
				.toList();
	}

}

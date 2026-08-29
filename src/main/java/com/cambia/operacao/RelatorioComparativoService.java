package com.cambia.operacao;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
class RelatorioComparativoService {

	private final FechamentoService fechamentoService;

	RelatorioComparativoService(FechamentoService fechamentoService) {
		this.fechamentoService = fechamentoService;
	}

	ComparativoResponse calcular(Periodo periodo, LocalDate hoje) {
		IntervaloDatas intervalo = PeriodoResolver.resolver(periodo, hoje);
		IntervaloDatas intervaloAnterior = PeriodoResolver.anterior(periodo, hoje);
		return calcularParaIntervalos(periodo, intervalo, intervaloAnterior);
	}

	/**
	 * Compara dois períodos escolhidos livremente pelo usuário — não precisam ser
	 * adjacentes nem seguir nenhuma convenção de calendário (ex: "2 meses atrás" vs
	 * "mês passado"). Período A ocupa os campos "principais" da resposta; Período B
	 * ocupa os campos de "período anterior", só como reaproveitamento da mesma estrutura.
	 */
	ComparativoResponse calcularEntreDoisPeriodos(LocalDate inicioA, LocalDate fimA, LocalDate inicioB,
			LocalDate fimB) {
		IntervaloDatas intervaloA = new IntervaloDatas(inicioA, fimA);
		IntervaloDatas intervaloB = new IntervaloDatas(inicioB, fimB);
		return calcularParaIntervalos(null, intervaloA, intervaloB);
	}

	private ComparativoResponse calcularParaIntervalos(Periodo periodo, IntervaloDatas intervalo,
			IntervaloDatas intervaloAnterior) {
		List<PontoSerieTemporal> serie = serieTemporal(intervalo);

		int totalOperacoes = somarQuantidade(serie);
		Map<String, BigDecimal> volumePorMoeda = somarVolumePorMoeda(serie);
		BigDecimal totalReais = somar(serie, PontoSerieTemporal::totalReais);
		BigDecimal totalComissaoLiquida = somar(serie, PontoSerieTemporal::totalComissaoLiquida);
		BigDecimal mediaDiaria = totalComissaoLiquida.divide(BigDecimal.valueOf(serie.size()), 2,
				RoundingMode.HALF_UP);

		PontoSerieTemporal melhorDia = serie.stream()
				.filter(p -> p.totalComissaoLiquida().compareTo(BigDecimal.ZERO) > 0)
				.max(Comparator.comparing(PontoSerieTemporal::totalComissaoLiquida))
				.orElse(null);
		PontoSerieTemporal piorDia = serie.stream()
				.filter(p -> p.totalComissaoLiquida().compareTo(BigDecimal.ZERO) > 0)
				.min(Comparator.comparing(PontoSerieTemporal::totalComissaoLiquida))
				.orElse(null);

		List<PontoSerieTemporal> serieAnterior = serieTemporal(intervaloAnterior);
		int totalOperacoesAnterior = somarQuantidade(serieAnterior);
		Map<String, BigDecimal> volumePorMoedaAnterior = somarVolumePorMoeda(serieAnterior);
		BigDecimal totalReaisAnterior = somar(serieAnterior, PontoSerieTemporal::totalReais);
		BigDecimal totalComissaoAnterior = somar(serieAnterior, PontoSerieTemporal::totalComissaoLiquida);
		PeriodoComparado periodoAnterior = new PeriodoComparado(intervaloAnterior.inicio(), intervaloAnterior.fim(),
				totalOperacoesAnterior, volumePorMoedaAnterior, totalReaisAnterior, totalComissaoAnterior);

		BigDecimal variacaoPercentual = totalComissaoAnterior.compareTo(BigDecimal.ZERO) == 0 ? null
				: totalComissaoLiquida.subtract(totalComissaoAnterior)
						.divide(totalComissaoAnterior, 4, RoundingMode.HALF_UP)
						.multiply(BigDecimal.valueOf(100))
						.setScale(2, RoundingMode.HALF_UP);

		return new ComparativoResponse(periodo, intervalo.inicio(), intervalo.fim(), serie, totalOperacoes,
				volumePorMoeda, totalReais, totalComissaoLiquida, mediaDiaria, melhorDia, piorDia, periodoAnterior,
				variacaoPercentual);
	}

	private List<PontoSerieTemporal> serieTemporal(IntervaloDatas intervalo) {
		return intervalo.inicio().datesUntil(intervalo.fim().plusDays(1))
				.map(dia -> {
					ResultadoFinanceiro resultado = fechamentoService.calcularResultadoDoDia(dia);
					return new PontoSerieTemporal(dia, resultado.quantidadeOperacoes(), resultado.volumePorMoeda(),
							resultado.totalReais(), resultado.totalComissaoLiquida());
				})
				.toList();
	}

	private BigDecimal somar(List<PontoSerieTemporal> serie, Function<PontoSerieTemporal, BigDecimal> extrator) {
		return serie.stream().map(extrator).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
	}

	private int somarQuantidade(List<PontoSerieTemporal> serie) {
		return serie.stream().mapToInt(PontoSerieTemporal::quantidadeOperacoes).sum();
	}

	private Map<String, BigDecimal> somarVolumePorMoeda(List<PontoSerieTemporal> serie) {
		Map<String, BigDecimal> total = new LinkedHashMap<>();
		for (PontoSerieTemporal ponto : serie) {
			ponto.volumePorMoeda().forEach((moeda, volume) -> total.merge(moeda, volume, BigDecimal::add));
		}
		return total;
	}

}

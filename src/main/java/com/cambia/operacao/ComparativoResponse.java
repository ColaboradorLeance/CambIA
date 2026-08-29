package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

record ComparativoResponse(
		Periodo periodo,
		LocalDate inicio,
		LocalDate fim,
		List<PontoSerieTemporal> serieTemporal,
		int totalOperacoes,
		Map<String, BigDecimal> volumePorMoeda,
		BigDecimal totalReais,
		BigDecimal totalComissaoLiquida,
		BigDecimal mediaDiariaComissaoLiquida,
		PontoSerieTemporal melhorDia,
		PontoSerieTemporal piorDia,
		PeriodoComparado periodoAnterior,
		BigDecimal variacaoComissaoLiquidaPercentual) {
}

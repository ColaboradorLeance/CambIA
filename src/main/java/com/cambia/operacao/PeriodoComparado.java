package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

record PeriodoComparado(LocalDate inicio, LocalDate fim, int totalOperacoes, Map<String, BigDecimal> volumePorMoeda,
		BigDecimal totalReais, BigDecimal totalComissaoLiquida) {
}

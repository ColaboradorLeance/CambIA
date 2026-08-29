package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

record PontoSerieTemporal(LocalDate data, int quantidadeOperacoes, Map<String, BigDecimal> volumePorMoeda,
		BigDecimal totalReais, BigDecimal totalComissaoLiquida) {
}

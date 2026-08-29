package com.cambia.operacao;

import java.math.BigDecimal;
import java.util.Map;

record ResultadoFinanceiro(
		int quantidadeOperacoes,
		Map<String, BigDecimal> volumePorMoeda,
		Map<String, Integer> quantidadePorMoeda,
		BigDecimal totalReais,
		BigDecimal totalBrutoCambio,
		BigDecimal totalComissaoLiquida,
		BigDecimal ticketMedioReais,
		BigDecimal maiorOperacaoReais,
		BigDecimal menorOperacaoReais) {
}

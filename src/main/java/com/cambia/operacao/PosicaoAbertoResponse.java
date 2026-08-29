package com.cambia.operacao;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

record PosicaoAbertoResponse(
		int totalOperacoesEmAndamento,
		List<OperacaoEmAberto> operacoes,
		Map<String, BigDecimal> exposicaoPorMoeda,
		List<ExposicaoItem> exposicaoPorCliente,
		List<ExposicaoItem> exposicaoPorBanco) {
}

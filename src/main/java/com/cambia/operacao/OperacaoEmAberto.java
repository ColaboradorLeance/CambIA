package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;

record OperacaoEmAberto(
		Long id,
		String idTrade,
		LocalDate data,
		long diasEmAberto,
		String clienteNome,
		String bancoNome,
		String moeda,
		BigDecimal valorMe) {
}

package com.cambia.operacao;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;

/**
 * Filtros da listagem paginada (Incremento 80) — os mesmos das telas de Ordens, agora
 * aplicados no banco. Todos opcionais; texto é busca parcial sem diferenciar caixa.
 */
record FiltroOperacoes(
		StatusOperacao status,
		@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFechamento,
		String moeda,
		String valorMoeda,
		String cnpj,
		String nome,
		String idTrade,
		String cv,
		String tipo,
		String banco,
		String fundo,
		String criadoPor,
		String completadoPor) {
}

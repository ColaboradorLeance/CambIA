package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

record RankingsResponse(
		Periodo periodo,
		LocalDate inicio,
		LocalDate fim,
		List<QuebraItem> porCliente,
		List<QuebraItem> porBanco,
		List<QuebraItem> porMoeda,
		List<QuebraItem> porTipo,
		List<QuebraItem> porUsuarioCriador,
		List<QuebraItem> porUsuarioCompletador) {
}

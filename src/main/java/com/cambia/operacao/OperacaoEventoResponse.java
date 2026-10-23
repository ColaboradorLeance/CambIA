package com.cambia.operacao;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
record OperacaoEventoResponse(
		Long id,
		Long operacaoId,
		String idTrade,
		TipoEventoOperacao tipo,
		String usuarioNome,
		Instant criadoEm,
		OperacaoSnapshot dadosAnteriores,
		// Incremento 81 (K9): nome da chave de API que agiu; ausente quando foi pela tela.
		String chaveApiNome) {
}

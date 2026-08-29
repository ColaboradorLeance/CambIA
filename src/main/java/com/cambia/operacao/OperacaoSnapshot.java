package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;

record OperacaoSnapshot(
		LocalDate data,
		String codigoBanco,
		Long clienteId,
		String clienteNome,
		Long bancoId,
		String bancoNome,
		String cv,
		String prCrVir,
		String moeda,
		BigDecimal valorMe,
		BigDecimal spotAsset,
		BigDecimal nivelamento,
		BigDecimal taxaFinal) {

	static OperacaoSnapshot de(Operacao operacao, String clienteNome, String bancoNome) {
		return new OperacaoSnapshot(
				operacao.getData(),
				operacao.getCodigoBanco(),
				operacao.getClienteId(),
				clienteNome,
				operacao.getBancoId(),
				bancoNome,
				operacao.getCv(),
				operacao.getPrCrVir(),
				operacao.getMoeda(),
				operacao.getValorMe(),
				operacao.getSpotAsset(),
				operacao.getNivelamento(),
				operacao.getTaxaFinal());
	}

}

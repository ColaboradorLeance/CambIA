package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.LocalDate;

record OperacaoSnapshot(
		LocalDate data,
		String codigoBanco,
		String codigoOperacao,
		Long clienteId,
		String clienteNome,
		Long bancoId,
		String bancoNome,
		String cv,
		String prCrVir,
		String fundo,
		String spreadEmissao,
		String moeda,
		BigDecimal valorMe,
		BigDecimal spotAsset,
		BigDecimal nivelamento,
		BigDecimal taxaFinal,
		// Incremento 84 — null nos eventos gravados antes dele.
		Long calculoId,
		String calculoNome) {

	static OperacaoSnapshot de(Operacao operacao, String clienteNome, String bancoNome, String calculoNome) {
		return new OperacaoSnapshot(
				operacao.getData(),
				operacao.getCodigoBanco(),
				operacao.getCodigoOperacao(),
				operacao.getClienteId(),
				clienteNome,
				operacao.getBancoId(),
				bancoNome,
				operacao.getCv(),
				operacao.getPrCrVir(),
				operacao.getFundo(),
				operacao.getSpreadEmissao(),
				operacao.getMoeda(),
				operacao.getValorMe(),
				operacao.getSpotAsset(),
				operacao.getNivelamento(),
				operacao.getTaxaFinal(),
				operacao.getCalculoId(),
				calculoNome);
	}

}

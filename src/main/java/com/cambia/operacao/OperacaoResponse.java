package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record OperacaoResponse(
		Long id,
		String idTrade,
		LocalDate data,
		String codigoBanco,
		Long clienteId,
		String clienteNome,
		String clienteDocumento,
		Long bancoId,
		String bancoNome,
		String cv,
		String prCrVir,
		String moeda,
		BigDecimal valorMe,
		BigDecimal spotAsset,
		BigDecimal nivelamento,
		BigDecimal taxaFinal,
		BigDecimal reais,
		BigDecimal totalBrutoCambio,
		BigDecimal comissaoLiquida,
		BigDecimal valorAbsoluto,
		BigDecimal spreadLiquidacao,
		StatusOperacao status,
		String criadoPorNome,
		Instant criadoEm,
		String completadoPorNome,
		Instant completadoEm) {

	static OperacaoResponse from(Operacao operacao, ValoresCalculados valores, String criadoPorNome,
			String completadoPorNome, String clienteNome, String clienteDocumento, String bancoNome) {
		return new OperacaoResponse(
				operacao.getId(),
				operacao.getIdTrade(),
				operacao.getData(),
				operacao.getCodigoBanco(),
				operacao.getClienteId(),
				clienteNome,
				clienteDocumento,
				operacao.getBancoId(),
				bancoNome,
				operacao.getCv(),
				operacao.getPrCrVir(),
				operacao.getMoeda(),
				operacao.getValorMe(),
				operacao.getSpotAsset(),
				operacao.getNivelamento(),
				operacao.getTaxaFinal(),
				valores.reais(),
				valores.totalBrutoCambio(),
				valores.comissaoLiquida(),
				valores.valorAbsoluto(),
				valores.spreadLiquidacao(),
				operacao.getStatus(),
				criadoPorNome,
				operacao.getCriadoEm(),
				completadoPorNome,
				operacao.getCompletadoEm());
	}

}

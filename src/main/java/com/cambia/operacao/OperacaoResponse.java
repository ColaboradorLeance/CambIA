package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

record OperacaoResponse(
		Long id,
		String idTrade,
		LocalDate data,
		String codigoBanco,
		String codigoOperacao,
		Long clienteId,
		String clienteNome,
		String clienteDocumento,
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
		BigDecimal reais,
		BigDecimal totalBrutoCambio,
		BigDecimal comissaoLiquida,
		BigDecimal valorAbsoluto,
		BigDecimal spreadLiquidacao,
		BigDecimal custo,
		BigDecimal rebate,
		BigDecimal baseComissionamento,
		StatusOperacao status,
		String criadoPorNome,
		Instant criadoEm,
		String completadoPorNome,
		Instant completadoEm) {

	/**
	 * Cópia com os campos calculados zerados. Usada pelos relatórios que agregam
	 * dinheiro (ex: Rankings) pra manter a semântica de "só conta o confirmado" depois
	 * que o Incremento 76 passou a calcular os valores em qualquer status: a prévia de
	 * uma ordem em andamento aparece nas telas de Ordens, mas não pode somar comissão
	 * em ranking — exatamente o que o gate antigo (Incremento 39) garantia por tabela.
	 */
	OperacaoResponse semValoresCalculados() {
		return new OperacaoResponse(id, idTrade, data, codigoBanco, codigoOperacao, clienteId, clienteNome,
				clienteDocumento, bancoId, bancoNome, cv, prCrVir, fundo, spreadEmissao, moeda, valorMe, spotAsset,
				nivelamento, taxaFinal, null, null, null, null, null, null, null, null, status, criadoPorNome,
				criadoEm, completadoPorNome, completadoEm);
	}

	static OperacaoResponse from(Operacao operacao, ValoresCalculados valores, String criadoPorNome,
			String completadoPorNome, String clienteNome, String clienteDocumento, String bancoNome) {
		return new OperacaoResponse(
				operacao.getId(),
				operacao.getIdTrade(),
				operacao.getData(),
				operacao.getCodigoBanco(),
				operacao.getCodigoOperacao(),
				operacao.getClienteId(),
				clienteNome,
				clienteDocumento,
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
				valores.reais(),
				valores.totalBrutoCambio(),
				valores.comissaoLiquida(),
				valores.valorAbsoluto(),
				valores.spreadLiquidacao(),
				valores.custo(),
				valores.rebate(),
				valores.baseComissionamento(),
				operacao.getStatus(),
				criadoPorNome,
				operacao.getCriadoEm(),
				completadoPorNome,
				operacao.getCompletadoEm());
	}

}

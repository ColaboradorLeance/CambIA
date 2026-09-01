package com.cambia.operacao;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cambia.banco.FormulaComissao;

/**
 * Fórmulas confirmadas em docs/dominio.md a partir de linhas reais da planilha.
 * Quando C/V não é "C" nem "V", não há fórmula confirmada para o spread de Total Bruto —
 * Total Bruto do Câmbio e Comissão Líquida ficam sem valor (null) em vez de supor um
 * cálculo.
 */
class OperacaoCalculo {

	private static final int ESCALA = 2;

	// Achado de negócio: Spread liquidação é uma razão (não um valor monetário), por isso
	// tem escala própria — ver calcularSpreadLiquidacao.
	private static final int ESCALA_SPREAD = 3;

	static ValoresCalculados calcular(BigDecimal valorMe, BigDecimal nivelamento, BigDecimal taxaFinal, String cv,
			String formulaComissaoBanco, BigDecimal taxaRebateBanco) {
		BigDecimal reais = valorMe.multiply(taxaFinal).setScale(ESCALA, RoundingMode.HALF_UP);
		BigDecimal valorAbsoluto = taxaFinal.subtract(nivelamento).abs().multiply(valorMe)
				.setScale(ESCALA, RoundingMode.HALF_UP);
		BigDecimal spreadLiquidacao = calcularSpreadLiquidacao(nivelamento, taxaFinal, cv);

		BigDecimal totalBrutoCambio = calcularTotalBrutoCambio(valorMe, nivelamento, taxaFinal, cv);
		if (totalBrutoCambio == null) {
			return new ValoresCalculados(reais, null, null, valorAbsoluto, spreadLiquidacao);
		}

		BigDecimal comissaoLiquida = formulaComissaoBanco == null ? null
				: new FormulaComissao(formulaComissaoBanco).avaliar(totalBrutoCambio, taxaRebateBanco)
						.setScale(ESCALA, RoundingMode.HALF_UP);

		return new ValoresCalculados(reais, totalBrutoCambio, comissaoLiquida, valorAbsoluto, spreadLiquidacao);
	}

	private static BigDecimal calcularTotalBrutoCambio(BigDecimal valorMe, BigDecimal nivelamento,
			BigDecimal taxaFinal, String cv) {
		if ("V".equalsIgnoreCase(cv)) {
			return valorMe.multiply(nivelamento.subtract(taxaFinal)).setScale(ESCALA, RoundingMode.HALF_UP);
		}
		if ("C".equalsIgnoreCase(cv)) {
			return valorMe.multiply(taxaFinal.subtract(nivelamento)).setScale(ESCALA, RoundingMode.HALF_UP);
		}
		return null;
	}

	// Regra confirmada pelo usuário: C/V = "NA" entra no mesmo cálculo de "C" (compra) —
	// diferente da regra de Total Bruto do Câmbio acima, onde "NA" fica sem fórmula
	// (as duas regras são independentes, não é uma inconsistência). Razão decimal (não
	// percentual — ex: 0,020, não 2,0), 3 casas decimais, arredondamento padrão do
	// sistema (HALF_UP). O front-end multiplica por 100 e mostra o "%" na exibição.
	private static BigDecimal calcularSpreadLiquidacao(BigDecimal nivelamento, BigDecimal taxaFinal, String cv) {
		if ("V".equalsIgnoreCase(cv)) {
			return nivelamento.divide(taxaFinal, ESCALA_SPREAD, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
		}
		if ("C".equalsIgnoreCase(cv) || "NA".equalsIgnoreCase(cv)) {
			return taxaFinal.divide(nivelamento, ESCALA_SPREAD, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
		}
		return null;
	}

}

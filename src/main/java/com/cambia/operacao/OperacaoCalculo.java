package com.cambia.operacao;

import java.math.BigDecimal;
import java.math.RoundingMode;

import com.cambia.banco.FormulaComissao;

/**
 * Fórmulas confirmadas em docs/dominio.md a partir de linhas reais da planilha.
 * Quando C/V não é "C" nem "V", não há fórmula confirmada para o spread — Total Bruto
 * do Câmbio e Comissão Líquida ficam sem valor (null) em vez de supor um cálculo.
 */
class OperacaoCalculo {

	private static final int ESCALA = 2;

	static ValoresCalculados calcular(BigDecimal valorMe, BigDecimal nivelamento, BigDecimal taxaFinal, String cv,
			String formulaComissaoBanco, BigDecimal taxaRebateBanco) {
		BigDecimal reais = valorMe.multiply(taxaFinal).setScale(ESCALA, RoundingMode.HALF_UP);
		BigDecimal valorAbsoluto = taxaFinal.subtract(nivelamento).abs().multiply(valorMe)
				.setScale(ESCALA, RoundingMode.HALF_UP);

		BigDecimal totalBrutoCambio = calcularTotalBrutoCambio(valorMe, nivelamento, taxaFinal, cv);
		if (totalBrutoCambio == null) {
			return new ValoresCalculados(reais, null, null, valorAbsoluto);
		}

		BigDecimal comissaoLiquida = formulaComissaoBanco == null ? null
				: new FormulaComissao(formulaComissaoBanco).avaliar(totalBrutoCambio, taxaRebateBanco)
						.setScale(ESCALA, RoundingMode.HALF_UP);

		return new ValoresCalculados(reais, totalBrutoCambio, comissaoLiquida, valorAbsoluto);
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

}

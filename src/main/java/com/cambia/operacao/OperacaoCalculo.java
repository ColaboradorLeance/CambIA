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

	static ValoresCalculados calcular(BigDecimal valorMe, BigDecimal spotAsset, BigDecimal nivelamento,
			BigDecimal taxaFinal, String cv, String prCrVir, String formulaComissaoBanco,
			BigDecimal taxaRebateBanco) {
		BigDecimal reais = valorMe.multiply(taxaFinal).setScale(ESCALA, RoundingMode.HALF_UP);
		BigDecimal valorAbsoluto = taxaFinal.subtract(nivelamento).abs().multiply(valorMe)
				.setScale(ESCALA, RoundingMode.HALF_UP);
		BigDecimal spreadLiquidacao = calcularSpreadLiquidacao(nivelamento, taxaFinal, cv);
		BigDecimal custo = calcularCusto(spotAsset, taxaFinal, prCrVir);
		BigDecimal rebate = calcularRebate(nivelamento, taxaFinal, spreadLiquidacao, prCrVir, taxaRebateBanco);

		BigDecimal totalBrutoCambio = calcularTotalBrutoCambio(valorMe, nivelamento, taxaFinal, cv);
		if (totalBrutoCambio == null) {
			return new ValoresCalculados(reais, null, null, valorAbsoluto, spreadLiquidacao, custo, rebate);
		}

		BigDecimal comissaoLiquida = formulaComissaoBanco == null ? null
				: new FormulaComissao(formulaComissaoBanco).avaliar(totalBrutoCambio, taxaRebateBanco)
						.setScale(ESCALA, RoundingMode.HALF_UP);

		return new ValoresCalculados(reais, totalBrutoCambio, comissaoLiquida, valorAbsoluto, spreadLiquidacao, custo,
				rebate);
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

	// Achado de negócio (Incremento 57): fórmula confirmada pelo usuário. Diferente de
	// Total Bruto/Spread liquidação (ficam null quando não há fórmula pro caso), Custo
	// usa 0 explicitamente pra qualquer tipo de ordem que não seja "Crédito" (Pronto,
	// Virtual, ou qualquer outro valor) — pedido assim pelo usuário, não é "sem fórmula".
	// Usa Spot Asset, campo que até aqui não entrava em nenhuma fórmula confirmada.
	private static BigDecimal calcularCusto(BigDecimal spotAsset, BigDecimal taxaFinal, String prCrVir) {
		if ("Credito".equalsIgnoreCase(prCrVir)) {
			return spotAsset.divide(taxaFinal, ESCALA_SPREAD, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
		}
		return BigDecimal.ZERO.setScale(ESCALA_SPREAD, RoundingMode.HALF_UP);
	}

	// Achado de negócio (Incremento 64): fórmula confirmada pelo usuário — base de
	// comissionamento pro rebate. Pronto e Virtual reaproveitam o valor que Spread
	// liquidação já calculou pra essa operação (que já varia sozinho conforme C/V,
	// internamente); Crédito usa uma fórmula PRÓPRIA e FIXA (Nivelamento/Taxa Final − 1,
	// forma "venda" do spread), independente do C/V da operação — confirmado
	// explicitamente pelo usuário, não é o mesmo caminho que Pronto/Virtual tomam.
	// Taxa de rebate usada crua (sem dividir por 100 — também confirmado pelo usuário),
	// mesmo padrão de escala (3 casas, HALF_UP) dos outros campos desta família.
	private static BigDecimal calcularRebate(BigDecimal nivelamento, BigDecimal taxaFinal, BigDecimal spreadLiquidacao,
			String prCrVir, BigDecimal taxaRebateBanco) {
		if (taxaRebateBanco == null) {
			return null;
		}
		if ("Credito".equalsIgnoreCase(prCrVir)) {
			BigDecimal base = nivelamento.divide(taxaFinal, ESCALA_SPREAD, RoundingMode.HALF_UP).subtract(BigDecimal.ONE);
			return base.multiply(taxaRebateBanco).setScale(ESCALA_SPREAD, RoundingMode.HALF_UP);
		}
		if ("Pronto".equalsIgnoreCase(prCrVir) || "Virtual".equalsIgnoreCase(prCrVir)) {
			return spreadLiquidacao == null ? null
					: spreadLiquidacao.multiply(taxaRebateBanco).setScale(ESCALA_SPREAD, RoundingMode.HALF_UP);
		}
		return null;
	}

}

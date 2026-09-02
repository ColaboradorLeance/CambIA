package com.cambia.operacao;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OperacaoCalculoTests {

	@Test
	void calculaVendaComNumerosRedondos() {
		// valorME=1000, nivelamento=5,10, taxaFinal=5,00, venda, fórmula do banco = 50%
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("50.00"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
		// venda: Nivelamento / Taxa Final − 1 = 5,10 / 5,00 − 1 = 0,020
		assertEquals(new BigDecimal("0.020"), valores.spreadLiquidacao());
		// tipo da ordem não é Crédito: custo = 0
		assertEquals(new BigDecimal("0.000"), valores.custo());
	}

	@Test
	void calculaCompraInvertendoSinalDoSpread() {
		// compra: a casa ganha a diferença ao contrário (taxaFinal - nivelamento)
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.00"), new BigDecimal("5.10"), "C",
				"Pronto", "NA", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5100.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("50.00"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
		// compra: Taxa Final / Nivelamento − 1 = 5,10 / 5,00 − 1 = 0,020
		assertEquals(new BigDecimal("0.020"), valores.spreadLiquidacao());
		assertEquals(new BigDecimal("0.000"), valores.custo());
	}

	@Test
	void cvDesconhecidoNaoCalculaTotalBrutoNemComissaoMasCalculaReaisEValorAbsoluto() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "XPTO",
				"Pronto", "NA", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertNull(valores.totalBrutoCambio());
		assertNull(valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
		// C/V sem fórmula confirmada (nem C, nem V, nem NA) — spread liquidação também null
		assertNull(valores.spreadLiquidacao());
	}

	// Achado de negócio: C/V = "NA" entra no MESMO cálculo de "C" (compra) pro spread de
	// liquidação — regra independente da de Total Bruto do Câmbio acima (onde "NA"
	// continua sem fórmula confirmada).
	@Test
	void cvNaoDisponivelCalculaSpreadLiquidacaoComoSeFosseCompra() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "NA",
				"Pronto", "NA", "N*50%", BigDecimal.ZERO);

		assertNull(valores.totalBrutoCambio());
		assertNull(valores.comissaoLiquida());
		// NA tratado como compra: Taxa Final / Nivelamento − 1 = 5,00 / 5,10 − 1 = −0,020
		assertEquals(new BigDecimal("-0.020"), valores.spreadLiquidacao());
	}

	@Test
	void semFormulaDoBancoNaoCalculaComissaoMasCalculaOResto() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", null, null);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertNull(valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
		assertEquals(new BigDecimal("0.020"), valores.spreadLiquidacao());
	}

	@Test
	void reproduzExemploRealDaPlanilhaTlxComFormulaReal() {
		// Linha real: venda, USD 885.242,40, Nivelamento 5,1960, Taxa Final 5,1856
		// Fórmula real informada pelo usuário: =N*70%-N*70%*4,65%
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("885242.40"), new BigDecimal("5.1990"), new BigDecimal("5.1960"),
				new BigDecimal("5.1856"), "V", "Pronto", "NA", "N*70%-N*70%*4,65%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("4590512.99"), valores.reais());
		assertEquals(new BigDecimal("9206.52"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("6144.89"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("9206.52"), valores.valorAbsoluto());
		// venda: 5,1960 / 5,1856 − 1 = 0,002005... → 0,002
		assertEquals(new BigDecimal("0.002"), valores.spreadLiquidacao());
	}

	@Test
	void reproduzExemploRealDaPlanilhaBzaComFormulaReal() {
		// Linha real: compra, USD 2.349,78, Nivelamento 5,2154, Taxa Final 5,2206
		// Fórmula real informada pelo usuário: =N*70% (sem desconto)
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("2349.78"), new BigDecimal("5.2200"), new BigDecimal("5.2154"),
				new BigDecimal("5.2206"), "C", "Pronto", "NA", "N*70%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("12267.26"), valores.reais());
		assertEquals(new BigDecimal("12.22"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("8.55"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("12.22"), valores.valorAbsoluto());
		// compra: 5,2206 / 5,2154 − 1 = 0,000997... → 0,001
		assertEquals(new BigDecimal("0.001"), valores.spreadLiquidacao());
	}

	// --- Achado de negócio (Incremento 57): campo Custo ---

	@Test
	void creditoCalculaCustoComSpotAssetETaxaFinal() {
		// Custo = Spot Asset / Taxa Final − 1, só quando o tipo da ordem é "Crédito"
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), new BigDecimal("5.00"), "V",
				"Credito", "0.015", "N*50%", BigDecimal.ZERO);

		// 5,10 / 5,00 − 1 = 0,020
		assertEquals(new BigDecimal("0.020"), valores.custo());
	}

	@Test
	void prontoNaoCalculaCusto() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("0.000"), valores.custo());
	}

	@Test
	void virtualTambemNaoCalculaCusto() {
		// A regra é especificamente "Crédito" — qualquer outro tipo (incluindo Virtual)
		// cai no "senão = 0", não só Pronto.
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), new BigDecimal("5.00"), "V",
				"Virtual", "0.015", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("0.000"), valores.custo());
	}

	// --- Achado de negócio (Incremento 64): campo Rebate ---

	@Test
	void prontoCalculaRebateComSpreadLiquidacaoVezesTaxaDeRebate() {
		// venda: spread liquidação = Nivelamento / Taxa Final − 1 = 5,10 / 5,00 − 1 = 0,020
		// taxa de rebate crua (sem dividir por 100, confirmado pelo usuário): 70
		// rebate = 0,020 × 70 = 1,400
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", "N*50%", new BigDecimal("70"));

		assertEquals(new BigDecimal("0.020"), valores.spreadLiquidacao());
		assertEquals(new BigDecimal("1.400"), valores.rebate());
	}

	@Test
	void virtualCalculaRebateComSpreadLiquidacaoVezesTaxaDeRebate() {
		// compra: spread liquidação = Taxa Final / Nivelamento − 1 = 5,10 / 5,00 − 1 = 0,020
		// mesma fórmula de Pronto — Virtual reaproveita a Spread liquidação, igual Pronto.
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.00"), new BigDecimal("5.10"), "C",
				"Virtual", "0.015", "N*50%", new BigDecimal("70"));

		assertEquals(new BigDecimal("0.020"), valores.spreadLiquidacao());
		assertEquals(new BigDecimal("1.400"), valores.rebate());
	}

	// Achado de negócio: Crédito usa uma fórmula PRÓPRIA e FIXA (Nivelamento/Taxa Final − 1),
	// independente do C/V da operação — confirmado explicitamente pelo usuário. Este teste
	// prova a independência: cv="C" faria a Spread liquidação dar −0,020 (Taxa Final /
	// Nivelamento − 1 = 5,00/5,10−1), mas o Rebate de Crédito ignora isso e usa
	// Nivelamento/Taxa Final − 1 = +0,020 de qualquer jeito.
	@Test
	void creditoCalculaRebateComFormulaFixaIndependenteDoCV() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "C",
				"Credito", "0.015", "N*50%", new BigDecimal("70"));

		// a Spread liquidação (pra C/V) dá negativo — confirma que o Rebate NÃO reaproveita
		assertEquals(new BigDecimal("-0.020"), valores.spreadLiquidacao());
		// mas o Rebate de Crédito usa a fórmula fixa: 5,10/5,00−1 = 0,020; × 70 = 1,400
		assertEquals(new BigDecimal("1.400"), valores.rebate());
	}

	@Test
	void taxaDeRebateNulaNaoCalculaRebate() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", null, null);

		assertNull(valores.rebate());
	}

	// --- Achado de negócio (Incremento 65): campo Base de comissionamento ---

	@Test
	void prontoBaseDeComissionamentoEhIgualAoRebate() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", "N*50%", new BigDecimal("70"));

		assertEquals(new BigDecimal("1.400"), valores.rebate());
		assertEquals(valores.rebate(), valores.baseComissionamento());
	}

	// Crédito: Base de comissionamento = Spread emissão − Custo + Rebate.
	@Test
	void creditoBaseDeComissionamentoUsaSpreadEmissaoMenosCustoMaisRebate() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Credito", "0.050", "N*50%", new BigDecimal("70"));

		// custo = Spot Asset / Taxa Final − 1 = 5,10 / 5,00 − 1 = 0,020
		assertEquals(new BigDecimal("0.020"), valores.custo());
		// rebate (fórmula fixa de Crédito) = Nivelamento/Taxa Final − 1 × 70 = 0,020 × 70 = 1,400
		assertEquals(new BigDecimal("1.400"), valores.rebate());
		// base = 0,050 − 0,020 + 1,400 = 1,430
		assertEquals(new BigDecimal("1.430"), valores.baseComissionamento());
	}

	// Virtual: mesma fórmula de Crédito (Spread emissão − Custo + Rebate), mas Custo é
	// sempre 0 pra Virtual (regra do Incremento 57) — na prática vira Spread emissão + Rebate.
	@Test
	void virtualBaseDeComissionamentoUsaSpreadEmissaoMaisRebateJaQueCustoEhZero() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Virtual", "0.050", "N*50%", new BigDecimal("70"));

		assertEquals(new BigDecimal("0.000"), valores.custo());
		assertEquals(new BigDecimal("1.400"), valores.rebate());
		// base = 0,050 − 0,000 + 1,400 = 1,450
		assertEquals(new BigDecimal("1.450"), valores.baseComissionamento());
	}

	@Test
	void semRebateNaoCalculaBaseDeComissionamento() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.05"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V",
				"Pronto", "NA", null, null);

		assertNull(valores.rebate());
		assertNull(valores.baseComissionamento());
	}

}

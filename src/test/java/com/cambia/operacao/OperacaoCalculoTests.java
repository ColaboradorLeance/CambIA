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
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("50.00"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
	}

	@Test
	void calculaCompraInvertendoSinalDoSpread() {
		// compra: a casa ganha a diferença ao contrário (taxaFinal - nivelamento)
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.00"), new BigDecimal("5.10"), "C", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5100.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("50.00"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
	}

	@Test
	void cvDesconhecidoNaoCalculaTotalBrutoNemComissaoMasCalculaReaisEValorAbsoluto() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), "NA", "N*50%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertNull(valores.totalBrutoCambio());
		assertNull(valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
	}

	@Test
	void semFormulaDoBancoNaoCalculaComissaoMasCalculaOResto() {
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("1000"), new BigDecimal("5.10"), new BigDecimal("5.00"), "V", null, null);

		assertEquals(new BigDecimal("5000.00"), valores.reais());
		assertEquals(new BigDecimal("100.00"), valores.totalBrutoCambio());
		assertNull(valores.comissaoLiquida());
		assertEquals(new BigDecimal("100.00"), valores.valorAbsoluto());
	}

	@Test
	void reproduzExemploRealDaPlanilhaTlxComFormulaReal() {
		// Linha real: venda, USD 885.242,40, Nivelamento 5,1960, Taxa Final 5,1856
		// Fórmula real informada pelo usuário: =N*70%-N*70%*4,65%
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("885242.40"), new BigDecimal("5.1960"), new BigDecimal("5.1856"), "V",
				"N*70%-N*70%*4,65%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("4590512.99"), valores.reais());
		assertEquals(new BigDecimal("9206.52"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("6144.89"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("9206.52"), valores.valorAbsoluto());
	}

	@Test
	void reproduzExemploRealDaPlanilhaBzaComFormulaReal() {
		// Linha real: compra, USD 2.349,78, Nivelamento 5,2154, Taxa Final 5,2206
		// Fórmula real informada pelo usuário: =N*70% (sem desconto)
		ValoresCalculados valores = OperacaoCalculo.calcular(
				new BigDecimal("2349.78"), new BigDecimal("5.2154"), new BigDecimal("5.2206"), "C", "N*70%", BigDecimal.ZERO);

		assertEquals(new BigDecimal("12267.26"), valores.reais());
		assertEquals(new BigDecimal("12.22"), valores.totalBrutoCambio());
		assertEquals(new BigDecimal("8.55"), valores.comissaoLiquida());
		assertEquals(new BigDecimal("12.22"), valores.valorAbsoluto());
	}

}

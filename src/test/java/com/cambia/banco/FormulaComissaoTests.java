package com.cambia.banco;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FormulaComissaoTests {

	private static final BigDecimal ZERO = BigDecimal.ZERO;

	@Test
	void avaliaPercentualSimples() {
		FormulaComissao formula = new FormulaComissao("N*70%");
		assertEquals(new BigDecimal("700.00"), formula.avaliar(new BigDecimal("1000"), ZERO).setScale(2));
	}

	@Test
	void avaliaFormulaRealDoBancoTlxComDesconto() {
		// Exemplo real informado pelo usuário: =N4*70%-N4*70%*4,65%
		FormulaComissao formula = new FormulaComissao("N*70%-N*70%*4,65%");
		BigDecimal resultado = formula.avaliar(new BigDecimal("9206.52"), ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
		assertEquals(new BigDecimal("6144.89"), resultado);
	}

	@Test
	void avaliaFormulaComParenteses() {
		FormulaComissao formula = new FormulaComissao("N*(70%-4,65%)");
		BigDecimal resultado = formula.avaliar(new BigDecimal("1000"), ZERO).setScale(2, java.math.RoundingMode.HALF_UP);
		assertEquals(new BigDecimal("653.50"), resultado);
	}

	@Test
	void aceitaDecimalComPontoOuVirgula() {
		FormulaComissao comVirgula = new FormulaComissao("N*4,65%");
		FormulaComissao comPonto = new FormulaComissao("N*4.65%");
		assertEquals(comVirgula.avaliar(new BigDecimal("1000"), ZERO), comPonto.avaliar(new BigDecimal("1000"), ZERO));
	}

	@Test
	void avaliaFormulaUsandoTaxaDeRebate() {
		// R representa a Taxa de Rebate do banco; aceita o mesmo sufixo % que N
		FormulaComissao formula = new FormulaComissao("N*R%");
		BigDecimal resultado = formula.avaliar(new BigDecimal("1000"), new BigDecimal("60"))
				.setScale(2, java.math.RoundingMode.HALF_UP);
		assertEquals(new BigDecimal("600.00"), resultado);
	}

	@Test
	void rejeitaFormulaComSintaxeInvalida() {
		assertThrows(IllegalArgumentException.class, () -> new FormulaComissao("N*").avaliar(new BigDecimal("1000"), ZERO));
		assertThrows(IllegalArgumentException.class,
				() -> new FormulaComissao("N*(70%").avaliar(new BigDecimal("1000"), ZERO));
		assertThrows(IllegalArgumentException.class, () -> new FormulaComissao("abc").avaliar(new BigDecimal("1000"), ZERO));
	}

}

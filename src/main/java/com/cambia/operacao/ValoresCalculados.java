package com.cambia.operacao;

import java.math.BigDecimal;

record ValoresCalculados(BigDecimal reais, BigDecimal totalBrutoCambio, BigDecimal comissaoLiquida,
		BigDecimal valorAbsoluto, BigDecimal spreadLiquidacao, BigDecimal custo) {
}

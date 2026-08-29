package com.cambia.operacao;

import java.math.BigDecimal;

record QuebraItem(String rotulo, int quantidade, BigDecimal totalReais, BigDecimal totalComissaoLiquida) {
}

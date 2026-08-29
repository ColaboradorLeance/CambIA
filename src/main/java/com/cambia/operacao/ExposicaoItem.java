package com.cambia.operacao;

import java.math.BigDecimal;

record ExposicaoItem(String rotulo, String moeda, int quantidade, BigDecimal valorMe) {
}

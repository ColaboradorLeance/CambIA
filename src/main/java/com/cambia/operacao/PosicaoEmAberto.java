package com.cambia.operacao;

import java.math.BigDecimal;
import java.util.Map;

record PosicaoEmAberto(int totalOperacoesEmAndamento, Map<String, BigDecimal> exposicaoPorMoeda) {
}

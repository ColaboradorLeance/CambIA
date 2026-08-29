package com.cambia.operacao;

import java.util.List;
import java.util.Map;

record ResumoOperacional(int totalOperacoes, Map<String, Integer> porStatus, List<OperacaoResponse> operacoes) {
}

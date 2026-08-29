package com.cambia.operacao;

import java.time.LocalDate;

record FechamentoResponse(
		LocalDate data,
		ResumoOperacional resumoOperacional,
		ResultadoFinanceiro resultadoFinanceiro,
		QuebrasPorDimensao quebras,
		PosicaoEmAberto posicaoEmAberto) {
}

package com.cambia.operacao;

import java.time.Instant;
import java.time.LocalDate;

record FechamentoHistoricoItem(Long id, LocalDate data, String formato, Instant geradoEm) {

	static FechamentoHistoricoItem from(FechamentoGerado gerado) {
		return new FechamentoHistoricoItem(gerado.getId(), gerado.getData(), gerado.getFormato(),
				gerado.getGeradoEm());
	}

}

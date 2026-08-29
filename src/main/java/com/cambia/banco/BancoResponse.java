package com.cambia.banco;

import java.math.BigDecimal;

record BancoResponse(
		Long id,
		String codigoBanco,
		String sigla,
		String nome,
		BigDecimal taxaRebate,
		Long calculoId,
		String calculoNome,
		String calculoFormula) {

	static BancoResponse from(Banco banco) {
		return new BancoResponse(
				banco.getId(),
				banco.getCodigoBanco(),
				banco.getSigla(),
				banco.getNome(),
				banco.getTaxaRebate(),
				banco.getCalculo().getId(),
				banco.getCalculo().getNome(),
				banco.getCalculo().getFormula());
	}

}

package com.cambia.banco;

import java.math.BigDecimal;
import java.util.List;

record BancoResponse(
		Long id,
		String codigoBanco,
		String sigla,
		String nome,
		BigDecimal taxaRebate,
		List<CalculoDoBanco> calculos) {

	/** Modelo de cálculo como aparece no banco (Incremento 84; ativo desde o 85). */
	record CalculoDoBanco(Long id, String nome, String formula, boolean ativo) {
	}

	static BancoResponse from(Banco banco) {
		return new BancoResponse(
				banco.getId(),
				banco.getCodigoBanco(),
				banco.getSigla(),
				banco.getNome(),
				banco.getTaxaRebate(),
				banco.getCalculos().stream()
						.map(calculo -> new CalculoDoBanco(calculo.getId(), calculo.getNome(), calculo.getFormula(),
								calculo.isAtivo()))
						.toList());
	}

}

package com.cambia.banco;

record CalculoResponse(Long id, String nome, String formula, boolean ativo) {

	static CalculoResponse from(Calculo calculo) {
		return new CalculoResponse(calculo.getId(), calculo.getNome(), calculo.getFormula(), calculo.isAtivo());
	}

}

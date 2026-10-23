package com.cambia.exportacao;

/**
 * Como o valor de uma coluna é escrito: no PDF vira texto formatado em pt-BR; no Excel,
 * célula do tipo certo (número, data) com o formato de exibição — pra planilha somar,
 * filtrar e ordenar de verdade, em vez de texto que parece número.
 */
public enum TipoColuna {
	TEXTO,
	INTEIRO,
	/** Dinheiro e valores em moeda: 2 casas. */
	DECIMAL,
	/** Cotações (Spot Asset, Nivelamento, Taxa Final): 4 casas. */
	TAXA,
	/** Bases sem unidade (Rebate, Base de comissionamento): 3 casas. */
	DECIMAL3,
	/** Razão decimal (0,020) exibida como percentual (2,0%). */
	PERCENTUAL,
	DATA,
	/** Texto de situação ("Confirmado", "Cancelado", "Em andamento"), colorido conforme o valor. */
	STATUS,
	DATA_HORA,
	/** Tipo decidido pelo valor de cada linha — pra tabelas de "indicador / valor". */
	AUTO
}

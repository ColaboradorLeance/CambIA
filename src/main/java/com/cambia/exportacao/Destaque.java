package com.cambia.exportacao;

/**
 * Número principal mostrado em cartão no topo do relatório (Incremento 83) — o que a pessoa
 * procura primeiro ("quanto deu de comissão?") sem ter que somar a tabela.
 */
public record Destaque(String rotulo, Object valor, TipoColuna tipo) {
}

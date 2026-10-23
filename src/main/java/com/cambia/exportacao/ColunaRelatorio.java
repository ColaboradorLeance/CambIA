package com.cambia.exportacao;

/**
 * @param noPdf {@code false} pra colunas que só saem no Excel — o PDF em paisagem fica
 * ilegível com as ~28 colunas de uma ordem, então ele traz as mesmas da tela.
 * @param totalizar soma a coluna numa linha de "Total" ao fim da tabela (Incremento 83)
 */
public record ColunaRelatorio(String titulo, TipoColuna tipo, boolean noPdf, boolean totalizar) {

	public static ColunaRelatorio de(String titulo, TipoColuna tipo) {
		return new ColunaRelatorio(titulo, tipo, true, false);
	}

	public static ColunaRelatorio soExcel(String titulo, TipoColuna tipo) {
		return new ColunaRelatorio(titulo, tipo, false, false);
	}

	public ColunaRelatorio somada() {
		return new ColunaRelatorio(titulo, tipo, noPdf, true);
	}

}

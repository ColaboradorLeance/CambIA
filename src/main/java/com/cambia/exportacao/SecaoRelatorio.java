package com.cambia.exportacao;

import java.util.List;

/** Uma tabela do relatório: no PDF, um bloco com título; no Excel, uma aba (ou um bloco, em uma aba só). */
public record SecaoRelatorio(String titulo, List<ColunaRelatorio> colunas, List<List<Object>> linhas) {

	public boolean temTotal() {
		return !linhas.isEmpty() && colunas.stream().anyMatch(ColunaRelatorio::totalizar);
	}

}

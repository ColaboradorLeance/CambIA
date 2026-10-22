package com.cambia.operacao;

import java.util.List;

/** Página de resultados (Incremento 80). {@code pagina} começa em 0. */
record PaginaResponse<T>(List<T> itens, long total, int pagina, int tamanho, int totalPaginas) {

	static <T> PaginaResponse<T> vazia(int pagina, int tamanho) {
		return new PaginaResponse<>(List.of(), 0, pagina, tamanho, 0);
	}

}

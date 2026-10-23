package com.cambia.exportacao;

import java.util.List;

/**
 * Relatório pronto pra exportar (Incremento 82), independente de formato: os exportadores
 * de PDF e Excel só sabem desenhar isto — cada relatório só monta o seu RelatorioTabular.
 *
 * @param cabecalho linhas de contexto (período, filtros, "gerado em") repetidas no topo
 * @param destaques números principais, em cartões no topo (Incremento 83)
 * @param umaAba no Excel, empilha as seções numa aba só em vez de uma aba por seção — pra
 * resumos curtos lidos de uma vez (o Fechamento Diário)
 * @param nomeArquivo sem extensão
 */
public record RelatorioTabular(String titulo, String nomeArquivo, List<String> cabecalho, List<Destaque> destaques,
		List<SecaoRelatorio> secoes, boolean umaAba) {

	public RelatorioTabular(String titulo, String nomeArquivo, List<String> cabecalho, List<SecaoRelatorio> secoes) {
		this(titulo, nomeArquivo, cabecalho, List.of(), secoes, false);
	}

	public RelatorioTabular(String titulo, String nomeArquivo, List<String> cabecalho, List<Destaque> destaques,
			List<SecaoRelatorio> secoes) {
		this(titulo, nomeArquivo, cabecalho, destaques, secoes, false);
	}

}

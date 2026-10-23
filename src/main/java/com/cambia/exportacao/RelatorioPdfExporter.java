package com.cambia.exportacao;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;
import com.lowagie.text.pdf.PdfWriter;

/**
 * PDF de qualquer {@link RelatorioTabular} — visual do Incremento 83: faixa de título na
 * cor da marca, cartões com os números principais, tabelas com cabeçalho colorido e linhas
 * alternadas, total ao fim, negativos em vermelho, situação da ordem colorida e rodapé
 * "Página X de Y" em todas as páginas. A4 paisagem; o relatório de Ordens traz no PDF as
 * mesmas 15 colunas da tela ({@link ColunaRelatorio#noPdf()}).
 */
public final class RelatorioPdfExporter {

	private static final float MARGEM = 28;
	private static final float ALTURA_RODAPE = 22;

	private static final Font TITULO = fonte(FontFactory.HELVETICA_BOLD, 17, Paleta.BRANCO);
	private static final Font SUBTITULO = fonte(FontFactory.HELVETICA, 9, Paleta.MARCA_CLARA);
	private static final Font MARCA = fonte(FontFactory.HELVETICA_BOLD, 10, Paleta.BRANCO);
	private static final Font ROTULO_DESTAQUE = fonte(FontFactory.HELVETICA, 8, Paleta.TEXTO_SECUNDARIO);
	private static final Font VALOR_DESTAQUE = fonte(FontFactory.HELVETICA_BOLD, 15, Paleta.MARCA);
	private static final Font SECAO = fonte(FontFactory.HELVETICA_BOLD, 12, Paleta.TEXTO);
	private static final Font VAZIO = fonte(FontFactory.HELVETICA_OBLIQUE, 9, Paleta.TEXTO_SECUNDARIO);

	// Larguras de coluna medidas em PONTOS, com a fonte real (ver larguras()).
	private static final int LINHAS_AMOSTRADAS = 200;
	private static final float LARGURA_UTIL = PageSize.A4.rotate().getWidth() - 2 * MARGEM;
	private static final float ESPACO_INTERNO = 9f; // padding da célula + folga
	private static final float MINIMO = 18f;
	private static final float TETO_DO_MINIMO = 120f;
	private static final float TETO_DESEJADO = 220f;

	private RelatorioPdfExporter() {
	}

	public static byte[] gerar(RelatorioTabular relatorio) {
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			Document documento = new Document(PageSize.A4.rotate(), MARGEM, MARGEM, MARGEM, MARGEM + ALTURA_RODAPE);
			PdfWriter escritor = PdfWriter.getInstance(documento, saida);
			escritor.setPageEvent(new Rodape(relatorio.titulo()));
			documento.open();

			documento.add(faixaDeTitulo(relatorio));
			if (!relatorio.destaques().isEmpty()) {
				documento.add(cartoes(relatorio.destaques()));
			}
			for (SecaoRelatorio secao : relatorio.secoes()) {
				adicionarSecao(documento, secao);
			}

			documento.close();
			return carimbarPaginacao(saida.toByteArray());
		} catch (DocumentException | IOException e) {
			throw new IllegalStateException("Falha ao gerar o PDF do relatório " + relatorio.titulo(), e);
		}
	}

	// ------------------------------------------------------------------ topo

	private static PdfPTable faixaDeTitulo(RelatorioTabular relatorio) throws DocumentException {
		PdfPTable faixa = new PdfPTable(new float[] { 5, 1 });
		faixa.setWidthPercentage(100);
		faixa.setSpacingAfter(10);

		PdfPCell esquerda = semBorda(Paleta.MARCA);
		esquerda.setPadding(12);
		esquerda.setPaddingBottom(14);
		esquerda.addElement(new Paragraph(relatorio.titulo(), TITULO));
		for (String linha : relatorio.cabecalho()) {
			Paragraph contexto = new Paragraph(linha, SUBTITULO);
			contexto.setSpacingBefore(1);
			esquerda.addElement(contexto);
		}
		faixa.addCell(esquerda);

		PdfPCell direita = semBorda(Paleta.MARCA);
		direita.setPadding(12);
		direita.setHorizontalAlignment(Element.ALIGN_RIGHT);
		Paragraph marca = new Paragraph("CambIA", MARCA);
		marca.setAlignment(Element.ALIGN_RIGHT);
		direita.addElement(marca);
		faixa.addCell(direita);
		return faixa;
	}

	private static PdfPTable cartoes(List<Destaque> destaques) {
		// Colunas de espaço entre os cartões: PdfPCell não tem margem externa.
		float[] larguras = new float[destaques.size() * 2 - 1];
		for (int i = 0; i < larguras.length; i++) {
			larguras[i] = i % 2 == 0 ? 10 : 0.6f;
		}
		PdfPTable tabela = new PdfPTable(larguras);
		tabela.setWidthPercentage(100);
		tabela.setSpacingAfter(6);
		for (int i = 0; i < destaques.size(); i++) {
			if (i > 0) {
				tabela.addCell(semBorda(null));
			}
			Destaque destaque = destaques.get(i);
			PdfPCell cartao = semBorda(Paleta.MARCA_SUAVE);
			cartao.setPadding(9);
			cartao.setBorderColorLeft(Paleta.MARCA);
			cartao.setBorderWidthLeft(3);
			cartao.addElement(new Paragraph(destaque.rotulo(), ROTULO_DESTAQUE));
			Font fonteValor = VALOR_DESTAQUE;
			if (destaque.valor() instanceof BigDecimal numero && numero.signum() < 0) {
				fonteValor = fonte(FontFactory.HELVETICA_BOLD, 15, Paleta.NEGATIVO);
			}
			cartao.addElement(new Paragraph(FormatoBrasileiro.formatar(destaque.valor(), destaque.tipo()), fonteValor));
			tabela.addCell(cartao);
		}
		return tabela;
	}

	// ------------------------------------------------------------------ tabelas

	private static void adicionarSecao(Document documento, SecaoRelatorio secao) throws DocumentException {
		Paragraph titulo = new Paragraph(secao.titulo(), SECAO);
		titulo.setSpacingBefore(12);
		titulo.setSpacingAfter(5);
		documento.add(titulo);

		if (secao.linhas().isEmpty()) {
			documento.add(new Paragraph("Nenhum registro.", VAZIO));
			return;
		}

		List<Integer> visiveis = IntStream.range(0, secao.colunas().size())
				.filter(indice -> secao.colunas().get(indice).noPdf())
				.boxed()
				.toList();

		// Fonte proporcional à largura: com 15 colunas em A4 paisagem, 9pt não cabe.
		float tamanho = visiveis.size() <= 6 ? 9f : visiveis.size() <= 10 ? 8f : 6.5f;
		Font fonteCabecalho = fonte(FontFactory.HELVETICA_BOLD, tamanho, Paleta.BRANCO);

		PdfPTable tabela = new PdfPTable(visiveis.size());
		// Tabela de 2-3 colunas esticada na página toda afasta o rótulo do valor e o olho
		// se perde na linha; ocupa só o necessário, alinhada à esquerda.
		float percentual = visiveis.size() <= 3 ? 55 : 100;
		tabela.setWidthPercentage(percentual);
		tabela.setHorizontalAlignment(Element.ALIGN_LEFT);
		tabela.setHeaderRows(1); // repete o cabeçalho no topo de cada página
		tabela.setWidths(larguras(secao, visiveis, tamanho, LARGURA_UTIL * percentual / 100));

		for (int indice : visiveis) {
			ColunaRelatorio coluna = secao.colunas().get(indice);
			PdfPCell celula = semBorda(Paleta.MARCA);
			celula.setPhrase(new Phrase(coluna.titulo(), fonteCabecalho));
			celula.setPadding(4);
			celula.setPaddingBottom(5);
			celula.setVerticalAlignment(Element.ALIGN_MIDDLE);
			if (numerico(coluna.tipo())) {
				celula.setHorizontalAlignment(Element.ALIGN_RIGHT);
			}
			tabela.addCell(celula);
		}

		for (int linha = 0; linha < secao.linhas().size(); linha++) {
			Color fundo = linha % 2 == 1 ? Paleta.LINHA_ALTERNADA : Paleta.BRANCO;
			for (int indice : visiveis) {
				ColunaRelatorio coluna = secao.colunas().get(indice);
				tabela.addCell(celulaDeDado(secao.linhas().get(linha).get(indice), coluna.tipo(), tamanho, fundo, false));
			}
		}

		if (secao.temTotal()) {
			adicionarTotal(tabela, secao, visiveis, tamanho);
		}
		documento.add(tabela);
	}

	private static void adicionarTotal(PdfPTable tabela, SecaoRelatorio secao, List<Integer> visiveis, float tamanho) {
		boolean rotuloEscrito = false;
		for (int indice : visiveis) {
			ColunaRelatorio coluna = secao.colunas().get(indice);
			if (coluna.totalizar()) {
				tabela.addCell(celulaDeDado(somar(secao, indice), coluna.tipo(), tamanho, Paleta.MARCA_SUAVE, true));
			} else {
				// "Total" na primeira coluna sem soma; as demais ficam em branco (não "—").
				tabela.addCell(celulaDeDado(rotuloEscrito ? "" : "Total", TipoColuna.TEXTO, tamanho,
						Paleta.MARCA_SUAVE, true));
				rotuloEscrito = true;
			}
		}
	}

	static BigDecimal somar(SecaoRelatorio secao, int indice) {
		BigDecimal total = BigDecimal.ZERO;
		for (List<Object> linha : secao.linhas()) {
			Object valor = linha.get(indice);
			if (valor != null) {
				total = total.add(new BigDecimal(valor.toString()));
			}
		}
		return total;
	}

	private static PdfPCell celulaDeDado(Object valor, TipoColuna declarado, float tamanho, Color fundo, boolean total) {
		TipoColuna tipo = declarado == TipoColuna.AUTO ? FormatoBrasileiro.tipoDe(valor) : declarado;
		Color cor = Paleta.TEXTO;
		if (valor instanceof BigDecimal numero && numero.signum() < 0) {
			cor = Paleta.NEGATIVO;
		} else if (tipo == TipoColuna.STATUS && Paleta.corDoStatus(String.valueOf(valor)) != null) {
			cor = Paleta.corDoStatus(String.valueOf(valor));
		}
		boolean negrito = total || tipo == TipoColuna.STATUS;
		Font fonte = fonte(negrito ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, tamanho, cor);

		PdfPCell celula = semBorda(fundo);
		celula.setPhrase(new Phrase(FormatoBrasileiro.formatar(valor, tipo), fonte));
		celula.setPadding(3.5f);
		celula.setPaddingBottom(4.5f);
		celula.setBorder(total ? Rectangle.TOP : Rectangle.BOTTOM);
		celula.setBorderColor(total ? Paleta.MARCA : Paleta.BORDA);
		celula.setBorderWidth(total ? 1f : 0.5f);
		cobrirEmendas(celula, fundo);
		if (valor != null && numerico(tipo)) {
			celula.setHorizontalAlignment(Element.ALIGN_RIGHT);
		}
		return celula;
	}

	/**
	 * Largura de cada coluna, medida em pontos com a fonte real (negrito no cabeçalho e no
	 * status). Contar caracteres não bastava: com nomes longos de cliente/banco ao lado,
	 * "Fundo" e "Moeda" quebravam em "Fund o"/"Moe da" (visto no PDF renderizado).
	 *
	 * <p>Cada coluna tem um MÍNIMO — o que cabe a maior palavra do título e o maior "pedaço
	 * sem espaço" dos valores (uma data inteira) — e um DESEJADO, o valor mais longo numa
	 * linha só. Todas recebem o mínimo primeiro; o que sobra da página é distribuído na
	 * proporção do que cada uma ainda quer. Assim, só texto com espaços (nomes) quebra de
	 * linha — e sempre entre palavras.
	 */
	private static float[] larguras(SecaoRelatorio secao, List<Integer> visiveis, float tamanho, float disponivel) {
		BaseFont regular = fonte(FontFactory.HELVETICA, tamanho, Paleta.TEXTO).getCalculatedBaseFont(false);
		BaseFont negrito = fonte(FontFactory.HELVETICA_BOLD, tamanho, Paleta.TEXTO).getCalculatedBaseFont(false);
		int amostra = Math.min(secao.linhas().size(), LINHAS_AMOSTRADAS);

		float[] minimos = new float[visiveis.size()];
		float[] desejados = new float[visiveis.size()];
		for (int i = 0; i < visiveis.size(); i++) {
			int indice = visiveis.get(i);
			ColunaRelatorio coluna = secao.colunas().get(indice);
			float minimo = maiorPalavra(coluna.titulo(), negrito, tamanho);
			float desejado = minimo;
			for (int linha = 0; linha < amostra; linha++) {
				Object valor = secao.linhas().get(linha).get(indice);
				TipoColuna tipo = coluna.tipo() == TipoColuna.AUTO ? FormatoBrasileiro.tipoDe(valor) : coluna.tipo();
				String texto = FormatoBrasileiro.formatar(valor, tipo);
				BaseFont fonte = tipo == TipoColuna.STATUS ? negrito : regular;
				desejado = Math.max(desejado, fonte.getWidthPoint(texto, tamanho));
				minimo = Math.max(minimo, maiorPalavra(texto, fonte, tamanho));
			}
			if (coluna.totalizar()) {
				String total = FormatoBrasileiro.formatar(somar(secao, indice), coluna.tipo());
				desejado = Math.max(desejado, negrito.getWidthPoint(total, tamanho));
				minimo = Math.max(minimo, negrito.getWidthPoint(total, tamanho));
			}
			minimos[i] = Math.clamp(minimo, MINIMO, TETO_DO_MINIMO) + ESPACO_INTERNO;
			desejados[i] = Math.max(minimos[i], Math.min(desejado + ESPACO_INTERNO, TETO_DESEJADO));
		}

		float somaMinimos = 0;
		float somaDesejados = 0;
		for (int i = 0; i < minimos.length; i++) {
			somaMinimos += minimos[i];
			somaDesejados += desejados[i];
		}
		if (somaDesejados <= disponivel) {
			return desejados;
		}
		float sobra = disponivel - somaMinimos;
		if (sobra <= 0) {
			return minimos; // colunas demais pra página: proporcional ao mínimo
		}
		float[] larguras = new float[minimos.length];
		for (int i = 0; i < minimos.length; i++) {
			larguras[i] = minimos[i] + sobra * (desejados[i] - minimos[i]) / (somaDesejados - somaMinimos);
		}
		return larguras;
	}

	private static float maiorPalavra(String texto, BaseFont fonte, float tamanho) {
		float maior = 0;
		for (String palavra : texto.split("\\s+")) {
			maior = Math.max(maior, fonte.getWidthPoint(palavra, tamanho));
		}
		return maior;
	}

	// ------------------------------------------------------------------ utilitários

	private static PdfPCell semBorda(Color fundo) {
		PdfPCell celula = new PdfPCell();
		celula.setBorder(Rectangle.NO_BORDER);
		if (fundo != null) {
			celula.setBackgroundColor(fundo);
			cobrirEmendas(celula, fundo);
		}
		return celula;
	}

	/**
	 * Fundos de células vizinhas não se encostam perfeitamente: os leitores de PDF desenham a
	 * emenda como um filete branco vertical (visto na faixa de título e nos cabeçalhos). Uma
	 * borda lateral fina da MESMA cor do fundo cobre a emenda sem aparecer.
	 */
	private static void cobrirEmendas(PdfPCell celula, Color fundo) {
		celula.setUseVariableBorders(true);
		celula.enableBorderSide(Rectangle.LEFT);
		celula.enableBorderSide(Rectangle.RIGHT);
		celula.setBorderWidthLeft(0.6f);
		celula.setBorderWidthRight(0.6f);
		celula.setBorderColorLeft(fundo);
		celula.setBorderColorRight(fundo);
	}

	private static Font fonte(String nome, float tamanho, Color cor) {
		return FontFactory.getFont(nome, tamanho, cor);
	}

	private static boolean numerico(TipoColuna tipo) {
		return tipo == TipoColuna.INTEIRO || tipo == TipoColuna.DECIMAL || tipo == TipoColuna.TAXA
				|| tipo == TipoColuna.DECIMAL3 || tipo == TipoColuna.PERCENTUAL;
	}

	/**
	 * "Página X de Y" no canto direito do rodapé. O total só existe depois de o documento
	 * fechar; carimbar numa segunda passada (PdfStamper) deixa o texto na própria página —
	 * um template preenchido no fim fica num XObject que busca e extração de texto não leem.
	 */
	private static byte[] carimbarPaginacao(byte[] pdf) throws IOException, DocumentException {
		PdfReader leitor = new PdfReader(pdf);
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			PdfStamper carimbo = new PdfStamper(leitor, saida);
			int total = leitor.getNumberOfPages();
			Font fonte = fonte(FontFactory.HELVETICA, Rodape.TAMANHO, Paleta.TEXTO_SECUNDARIO);
			for (int pagina = 1; pagina <= total; pagina++) {
				// "WithRotation": a página é paisagem (A4 rotacionado); sem isso a largura vem
				// a do retrato (595 em vez de 842) e o texto cai no meio do rodapé.
				Rectangle tamanho = leitor.getPageSizeWithRotation(pagina);
				ColumnText.showTextAligned(carimbo.getOverContent(pagina), Element.ALIGN_RIGHT,
						new Phrase("Página " + pagina + " de " + total, fonte), tamanho.getWidth() - MARGEM,
						Rodape.ALTURA_TEXTO, 0);
			}
			carimbo.close();
			return saida.toByteArray();
		} finally {
			leitor.close();
		}
	}

	/** Linha divisória e "CambIA · título" à esquerda, em toda página. */
	private static final class Rodape extends PdfPageEventHelper {

		static final float TAMANHO = 7.5f;
		static final float ALTURA_TEXTO = MARGEM - 4;
		private final String titulo;

		Rodape(String titulo) {
			this.titulo = titulo;
		}

		@Override
		public void onEndPage(PdfWriter escritor, Document documento) {
			PdfContentByte conteudo = escritor.getDirectContent();
			Rectangle pagina = documento.getPageSize();

			conteudo.setColorStroke(Paleta.BORDA);
			conteudo.setLineWidth(0.5f);
			conteudo.moveTo(MARGEM, ALTURA_TEXTO + 11);
			conteudo.lineTo(pagina.getWidth() - MARGEM, ALTURA_TEXTO + 11);
			conteudo.stroke();

			Font fonte = fonte(FontFactory.HELVETICA, TAMANHO, Paleta.TEXTO_SECUNDARIO);
			ColumnText.showTextAligned(conteudo, Element.ALIGN_LEFT, new Phrase("CambIA · " + titulo, fonte), MARGEM,
					ALTURA_TEXTO, 0);
		}

	}

}

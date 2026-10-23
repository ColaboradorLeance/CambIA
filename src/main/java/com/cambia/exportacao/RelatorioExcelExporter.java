package com.cambia.exportacao;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.PrintSetup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;

/**
 * Excel de qualquer {@link RelatorioTabular} — visual do Incremento 83: faixa de título na
 * cor da marca, cartões com os números principais, cabeçalho colorido, linhas alternadas,
 * linha de total com {@code SUBTOTAL} (acompanha o filtro: filtrar Status = Confirmado
 * mostra o total só das confirmadas), negativos em vermelho, situação colorida, sem a grade
 * cinza e já configurado pra imprimir em paisagem. Números e datas continuam células do
 * tipo certo — a planilha soma, filtra e ordena de verdade.
 *
 * <p>SXSSF (streaming): mantém só {@value #LINHAS_EM_MEMORIA} linhas por aba em memória e
 * descarrega o resto em arquivo temporário — um relatório de Ordens de um ano não monta a
 * planilha inteira na memória do servidor.
 */
public final class RelatorioExcelExporter {

	private static final int LINHAS_EM_MEMORIA = 100;
	private static final int TAMANHO_MAXIMO_NOME_ABA = 31;
	private static final int LARGURA_MINIMA_COLUNA = 10;
	private static final int LARGURA_MAXIMA_COLUNA = 60;
	// A largura vem do maior texto entre o título e as primeiras linhas — olhar todas
	// custaria uma passada extra sobre dados que o streaming já descartou da memória.
	private static final int LINHAS_AMOSTRADAS_PRA_LARGURA = 200;
	private static final int COLUNAS_MINIMAS_DA_FAIXA = 4;

	private RelatorioExcelExporter() {
	}

	public static byte[] gerar(RelatorioTabular relatorio) {
		SXSSFWorkbook planilha = new SXSSFWorkbook(LINHAS_EM_MEMORIA);
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			Estilos estilos = new Estilos(planilha);
			if (relatorio.umaAba()) {
				escreverTudoNumaAba(planilha, relatorio, estilos);
			} else {
				Set<String> nomesUsados = new HashSet<>();
				boolean primeira = true;
				for (SecaoRelatorio secao : relatorio.secoes()) {
					escreverAba(planilha, relatorio, secao, nomeUnico(secao.titulo(), nomesUsados), primeira, estilos);
					primeira = false;
				}
			}
			planilha.write(saida);
			return saida.toByteArray();
		} catch (IOException e) {
			throw new IllegalStateException("Falha ao gerar o Excel do relatório " + relatorio.titulo(), e);
		} finally {
			// Apaga os arquivos temporários do streaming. Falhar aqui não invalida o arquivo
			// já escrito — só deixa um temporário pro sistema operacional limpar.
			try {
				planilha.close();
			} catch (IOException e) {
				// sem ação: o conteúdo já foi gerado
			}
		}
	}

	// ------------------------------------------------------------------ layouts

	/** Uma aba por seção, com filtro automático e cabeçalho congelado. */
	private static void escreverAba(SXSSFWorkbook planilha, RelatorioTabular relatorio, SecaoRelatorio secao,
			String nomeAba, boolean primeira, Estilos estilos) {
		Sheet aba = prepararAba(planilha, nomeAba);
		int largura = Math.max(secao.colunas().size(), COLUNAS_MINIMAS_DA_FAIXA);
		int linha = escreverTopo(aba, relatorio, relatorio.titulo() + " · " + secao.titulo(), largura, estilos);
		if (primeira) {
			linha = escreverDestaques(aba, linha, relatorio.destaques(), largura, estilos);
		}

		Tabela tabela = escreverTabela(aba, linha, secao, estilos);
		aba.createFreezePane(0, tabela.linhaCabecalho() + 1);
		if (!secao.linhas().isEmpty()) {
			aba.setAutoFilter(new CellRangeAddress(tabela.linhaCabecalho(), tabela.ultimaLinhaDeDados(), 0,
					secao.colunas().size() - 1));
		}
		aba.setRepeatingRows(new CellRangeAddress(tabela.linhaCabecalho(), tabela.linhaCabecalho(), -1, -1));
		ajustarLarguras(aba, List.of(secao));
	}

	/** Resumo curto lido de uma vez (Fechamento Diário): seções empilhadas na mesma aba. */
	private static void escreverTudoNumaAba(SXSSFWorkbook planilha, RelatorioTabular relatorio, Estilos estilos) {
		Sheet aba = prepararAba(planilha, nomeUnico(relatorio.titulo(), new HashSet<>()));
		int largura = Math.max(COLUNAS_MINIMAS_DA_FAIXA,
				relatorio.secoes().stream().mapToInt(secao -> secao.colunas().size()).max().orElse(1));
		int linha = escreverTopo(aba, relatorio, relatorio.titulo(), largura, estilos);
		linha = escreverDestaques(aba, linha, relatorio.destaques(), largura, estilos);

		for (SecaoRelatorio secao : relatorio.secoes()) {
			Row titulo = aba.createRow(linha);
			titulo.setHeightInPoints(22);
			texto(titulo, 0, secao.titulo(), estilos.tituloSecao);
			for (int coluna = 1; coluna < largura; coluna++) {
				texto(titulo, coluna, "", estilos.tituloSecao);
			}
			linha = escreverTabela(aba, linha + 1, secao, estilos).proximaLinhaLivre() + 1;
		}
		ajustarLarguras(aba, relatorio.secoes());
	}

	private static Sheet prepararAba(SXSSFWorkbook planilha, String nome) {
		Sheet aba = planilha.createSheet(nome);
		aba.setDisplayGridlines(false);
		PrintSetup impressao = aba.getPrintSetup();
		impressao.setLandscape(true);
		impressao.setPaperSize(PrintSetup.A4_PAPERSIZE);
		aba.setFitToPage(true);
		impressao.setFitWidth((short) 1);
		impressao.setFitHeight((short) 0);
		return aba;
	}

	// ------------------------------------------------------------------ blocos

	/** Faixa de título na cor da marca + linhas de contexto (período, filtros, gerado em). */
	private static int escreverTopo(Sheet aba, RelatorioTabular relatorio, String titulo, int largura, Estilos estilos) {
		Row faixa = aba.createRow(0);
		faixa.setHeightInPoints(34);
		texto(faixa, 0, titulo, estilos.faixaTitulo);
		for (int coluna = 1; coluna < largura; coluna++) {
			texto(faixa, coluna, "", estilos.faixaTitulo);
		}
		aba.addMergedRegion(new CellRangeAddress(0, 0, 0, largura - 1));

		int linha = 1;
		for (String contexto : relatorio.cabecalho()) {
			Row row = aba.createRow(linha);
			row.setHeightInPoints(16);
			texto(row, 0, contexto, estilos.contexto);
			aba.addMergedRegion(new CellRangeAddress(linha, linha, 0, largura - 1));
			linha++;
		}
		return linha + 1;
	}

	/** Cartões: rótulo pequeno em cima, número grande embaixo, cada um ocupando N colunas. */
	private static int escreverDestaques(Sheet aba, int linha, List<Destaque> destaques, int largura, Estilos estilos) {
		if (destaques.isEmpty()) {
			return linha;
		}
		int span = Math.max(1, largura / destaques.size());
		Row rotulos = aba.createRow(linha);
		Row valores = aba.createRow(linha + 1);
		rotulos.setHeightInPoints(18);
		valores.setHeightInPoints(28);
		for (int i = 0; i < destaques.size(); i++) {
			Destaque destaque = destaques.get(i);
			int inicio = i * span;
			int fim = inicio + span - 1;
			for (int coluna = inicio; coluna <= fim; coluna++) {
				texto(rotulos, coluna, coluna == inicio ? destaque.rotulo() : "", estilos.rotuloCartao);
				Cell celula = valores.createCell(coluna);
				celula.setCellStyle(estilos.valorCartao(destaque.tipo()));
				if (coluna == inicio) {
					preencher(celula, destaque.valor(), destaque.tipo());
				}
			}
			if (span > 1) {
				aba.addMergedRegion(new CellRangeAddress(linha, linha, inicio, fim));
				aba.addMergedRegion(new CellRangeAddress(linha + 1, linha + 1, inicio, fim));
			}
		}
		return linha + 3;
	}

	private record Tabela(int linhaCabecalho, int ultimaLinhaDeDados, int proximaLinhaLivre) {
	}

	private static Tabela escreverTabela(Sheet aba, int linha, SecaoRelatorio secao, Estilos estilos) {
		int linhaCabecalho = linha;
		Row cabecalho = aba.createRow(linha++);
		cabecalho.setHeightInPoints(30);
		for (int coluna = 0; coluna < secao.colunas().size(); coluna++) {
			ColunaRelatorio definicao = secao.colunas().get(coluna);
			texto(cabecalho, coluna, definicao.titulo(), estilos.cabecalho(numerico(definicao.tipo())));
		}

		if (secao.linhas().isEmpty()) {
			Row vazio = aba.createRow(linha);
			texto(vazio, 0, "Nenhum registro.", estilos.contexto);
			return new Tabela(linhaCabecalho, linhaCabecalho, linha + 1);
		}

		int primeiraDeDados = linha;
		for (int i = 0; i < secao.linhas().size(); i++) {
			Row row = aba.createRow(linha++);
			row.setHeightInPoints(18);
			List<Object> valores = secao.linhas().get(i);
			boolean alternada = i % 2 == 1;
			for (int coluna = 0; coluna < secao.colunas().size(); coluna++) {
				Object valor = valores.get(coluna);
				TipoColuna declarado = secao.colunas().get(coluna).tipo();
				TipoColuna tipo = declarado == TipoColuna.AUTO ? FormatoBrasileiro.tipoDe(valor) : declarado;
				Cell celula = row.createCell(coluna);
				celula.setCellStyle(estilos.dado(tipo, alternada, statusDe(tipo, valor)));
				preencher(celula, valor, tipo);
			}
		}
		int ultimaDeDados = linha - 1;

		if (secao.temTotal()) {
			Row total = aba.createRow(linha++);
			total.setHeightInPoints(20);
			boolean rotuloEscrito = false;
			for (int coluna = 0; coluna < secao.colunas().size(); coluna++) {
				ColunaRelatorio definicao = secao.colunas().get(coluna);
				Cell celula = total.createCell(coluna);
				if (definicao.totalizar()) {
					String letra = CellReference.convertNumToColString(coluna);
					// 109 = soma ignorando linhas ocultas pelo filtro automático.
					celula.setCellFormula("SUBTOTAL(109," + letra + (primeiraDeDados + 1) + ":" + letra
							+ (ultimaDeDados + 1) + ")");
					celula.setCellStyle(estilos.total(definicao.tipo()));
				} else {
					celula.setCellStyle(estilos.total(TipoColuna.TEXTO));
					if (!rotuloEscrito) {
						celula.setCellValue("Total");
						rotuloEscrito = true;
					}
				}
			}
		}
		return new Tabela(linhaCabecalho, ultimaDeDados, linha);
	}

	// ------------------------------------------------------------------ células

	private static String statusDe(TipoColuna tipo, Object valor) {
		return tipo == TipoColuna.STATUS && valor != null && Paleta.corDoStatus(valor.toString()) != null
				? valor.toString() : null;
	}

	private static void preencher(Cell celula, Object valor, TipoColuna tipo) {
		if (valor == null) {
			return;
		}
		TipoColuna efetivo = tipo == TipoColuna.AUTO ? FormatoBrasileiro.tipoDe(valor) : tipo;
		switch (efetivo) {
			case INTEIRO, DECIMAL, TAXA, DECIMAL3, PERCENTUAL ->
					celula.setCellValue(new BigDecimal(valor.toString()).doubleValue());
			case DATA -> celula.setCellValue((LocalDate) valor);
			case DATA_HORA -> celula.setCellValue(((Instant) valor).atZone(FormatoBrasileiro.FUSO).toLocalDateTime());
			default -> celula.setCellValue(valor.toString());
		}
	}

	private static void texto(Row row, int coluna, String valor, CellStyle estilo) {
		Cell celula = row.createCell(coluna);
		celula.setCellValue(valor);
		celula.setCellStyle(estilo);
	}

	private static boolean numerico(TipoColuna tipo) {
		return tipo == TipoColuna.INTEIRO || tipo == TipoColuna.DECIMAL || tipo == TipoColuna.TAXA
				|| tipo == TipoColuna.DECIMAL3 || tipo == TipoColuna.PERCENTUAL;
	}

	private static void ajustarLarguras(Sheet aba, List<SecaoRelatorio> secoes) {
		Map<Integer, Integer> maiores = new HashMap<>();
		for (SecaoRelatorio secao : secoes) {
			for (int coluna = 0; coluna < secao.colunas().size(); coluna++) {
				ColunaRelatorio definicao = secao.colunas().get(coluna);
				int maior = definicao.titulo().length();
				int amostra = Math.min(secao.linhas().size(), LINHAS_AMOSTRADAS_PRA_LARGURA);
				for (int i = 0; i < amostra; i++) {
					Object valor = secao.linhas().get(i).get(coluna);
					TipoColuna tipo = definicao.tipo() == TipoColuna.AUTO ? FormatoBrasileiro.tipoDe(valor)
							: definicao.tipo();
					maior = Math.max(maior, FormatoBrasileiro.formatar(valor, tipo).length());
				}
				maiores.merge(coluna, maior, Math::max);
			}
		}
		maiores.forEach((coluna, maior) -> aba.setColumnWidth(coluna,
				Math.clamp(maior + 4, LARGURA_MINIMA_COLUNA, LARGURA_MAXIMA_COLUNA) * 256));
	}

	/** Excel limita o nome da aba a 31 caracteres e proíbe : \ / ? * [ ]. */
	private static String nomeUnico(String titulo, Set<String> usados) {
		String base = WorkbookUtil.createSafeSheetName(titulo.replace('/', '-'), '-');
		if (base.length() > TAMANHO_MAXIMO_NOME_ABA) {
			base = base.substring(0, TAMANHO_MAXIMO_NOME_ABA);
		}
		String nome = base;
		for (int n = 2; !usados.add(nome.toLowerCase()); n++) {
			String sufixo = " (" + n + ")";
			nome = base.substring(0, Math.min(base.length(), TAMANHO_MAXIMO_NOME_ABA - sufixo.length())) + sufixo;
		}
		return nome;
	}

	// ------------------------------------------------------------------ estilos

	/**
	 * Estilos criados uma vez e reaproveitados (cache por combinação): o Excel limita a
	 * ~64 mil estilos por arquivo, e um estilo por célula estouraria isso num relatório grande.
	 */
	private static final class Estilos {

		private final SXSSFWorkbook planilha;
		private final DataFormat formatos;
		private final Map<String, CellStyle> cache = new HashMap<>();

		final CellStyle faixaTitulo;
		final CellStyle contexto;
		final CellStyle tituloSecao;
		final CellStyle rotuloCartao;

		Estilos(SXSSFWorkbook planilha) {
			this.planilha = planilha;
			this.formatos = planilha.createDataFormat();

			faixaTitulo = novo(Paleta.MARCA);
			faixaTitulo.setFont(fonte(16, true, Paleta.BRANCO));
			faixaTitulo.setVerticalAlignment(VerticalAlignment.CENTER);
			faixaTitulo.setIndention((short) 1);

			contexto = novo(null);
			contexto.setFont(fonte(10, false, Paleta.TEXTO_SECUNDARIO));
			contexto.setIndention((short) 1);

			tituloSecao = novo(null);
			tituloSecao.setFont(fonte(12, true, Paleta.MARCA));
			tituloSecao.setVerticalAlignment(VerticalAlignment.BOTTOM);
			tituloSecao.setBorderBottom(BorderStyle.MEDIUM);
			((XSSFCellStyle) tituloSecao).setBottomBorderColor(cor(Paleta.MARCA));

			rotuloCartao = novo(Paleta.MARCA_SUAVE);
			rotuloCartao.setFont(fonte(9, false, Paleta.TEXTO_SECUNDARIO));
			rotuloCartao.setVerticalAlignment(VerticalAlignment.BOTTOM);
			rotuloCartao.setIndention((short) 1);
			rotuloCartao.setBorderLeft(BorderStyle.THICK);
			((XSSFCellStyle) rotuloCartao).setLeftBorderColor(cor(Paleta.MARCA));
		}

		CellStyle valorCartao(TipoColuna tipo) {
			return cache.computeIfAbsent("cartao:" + tipo, chave -> {
				CellStyle estilo = novo(Paleta.MARCA_SUAVE);
				estilo.setFont(fonte(15, true, Paleta.MARCA));
				estilo.setAlignment(HorizontalAlignment.LEFT);
				estilo.setVerticalAlignment(VerticalAlignment.CENTER);
				estilo.setIndention((short) 1);
				estilo.setBorderLeft(BorderStyle.THICK);
				((XSSFCellStyle) estilo).setLeftBorderColor(cor(Paleta.MARCA));
				aplicarFormato(estilo, tipo);
				return estilo;
			});
		}

		CellStyle cabecalho(boolean numerico) {
			return cache.computeIfAbsent("cabecalho:" + numerico, chave -> {
				CellStyle estilo = novo(Paleta.MARCA);
				estilo.setFont(fonte(10, true, Paleta.BRANCO));
				estilo.setAlignment(numerico ? HorizontalAlignment.RIGHT : HorizontalAlignment.LEFT);
				estilo.setVerticalAlignment(VerticalAlignment.CENTER);
				estilo.setWrapText(true);
				estilo.setIndention((short) 1);
				return estilo;
			});
		}

		CellStyle dado(TipoColuna tipo, boolean alternada, String status) {
			return cache.computeIfAbsent("dado:" + tipo + ":" + alternada + ":" + status, chave -> {
				CellStyle estilo = novo(alternada ? Paleta.LINHA_ALTERNADA : Paleta.BRANCO);
				estilo.setVerticalAlignment(VerticalAlignment.CENTER);
				estilo.setBorderBottom(BorderStyle.THIN);
				((XSSFCellStyle) estilo).setBottomBorderColor(cor(Paleta.BORDA));
				Color corTexto = status == null ? Paleta.TEXTO : Paleta.corDoStatus(status);
				estilo.setFont(fonte(10, status != null, corTexto));
				if (!numerico(tipo)) {
					estilo.setIndention((short) 1);
				}
				aplicarFormato(estilo, tipo);
				return estilo;
			});
		}

		CellStyle total(TipoColuna tipo) {
			return cache.computeIfAbsent("total:" + tipo, chave -> {
				CellStyle estilo = novo(Paleta.MARCA_SUAVE);
				estilo.setFont(fonte(10, true, Paleta.TEXTO));
				estilo.setVerticalAlignment(VerticalAlignment.CENTER);
				estilo.setBorderTop(BorderStyle.MEDIUM);
				((XSSFCellStyle) estilo).setTopBorderColor(cor(Paleta.MARCA));
				estilo.setIndention((short) 1);
				aplicarFormato(estilo, tipo);
				return estilo;
			});
		}

		// Formatos nativos do Excel (a exibição segue o idioma de quem abre — vírgula decimal
		// num Excel em português); a segunda seção pinta negativos de vermelho.
		private void aplicarFormato(CellStyle estilo, TipoColuna tipo) {
			String formato = switch (tipo) {
				case INTEIRO -> "#,##0;[Red]-#,##0";
				case DECIMAL -> "#,##0.00;[Red]-#,##0.00";
				case TAXA -> "#,##0.0000;[Red]-#,##0.0000";
				case DECIMAL3 -> "#,##0.000;[Red]-#,##0.000";
				case PERCENTUAL -> "0.0%;[Red]-0.0%";
				case DATA -> "dd/mm/yyyy";
				case DATA_HORA -> "dd/mm/yyyy hh:mm";
				default -> null;
			};
			if (formato != null) {
				estilo.setDataFormat(formatos.getFormat(formato));
			}
			if (numerico(tipo)) {
				estilo.setAlignment(HorizontalAlignment.RIGHT);
			}
		}

		private CellStyle novo(Color fundo) {
			XSSFCellStyle estilo = (XSSFCellStyle) planilha.createCellStyle();
			if (fundo != null) {
				estilo.setFillForegroundColor(cor(fundo));
				estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			}
			return estilo;
		}

		private XSSFFont fonte(int tamanho, boolean negrito, Color cor) {
			XSSFFont fonte = (XSSFFont) planilha.createFont();
			fonte.setFontName("Calibri");
			fonte.setFontHeightInPoints((short) tamanho);
			fonte.setBold(negrito);
			fonte.setColor(cor(cor));
			return fonte;
		}

		private static XSSFColor cor(Color cor) {
			return new XSSFColor(Paleta.rgb(cor), null);
		}

	}

}

package com.cambia.exportacao;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static com.cambia.exportacao.ColunaRelatorio.de;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremento 83 (pedido do usuário: exports "visualmente mais agradáveis e modernos, pra
 * facilitar a visualização dos dados"). Unitário, sem Spring: cada teste gera o arquivo a
 * partir de um relatório sintético e verifica, no próprio arquivo, a propriedade visual que
 * ajuda a ler os dados — faixa de título, cabeçalho destacado, linhas alternadas, total que
 * acompanha o filtro, negativos em vermelho, cartões de destaque, paginação.
 */
class ExportacaoVisualTests {

	private static final byte[] COR_MARCA = { (byte) 0x4F, (byte) 0x46, (byte) 0xE5 };

	private static RelatorioTabular relatorio(int linhas, boolean umaAba) {
		List<List<Object>> dados = new ArrayList<>();
		for (int i = 0; i < linhas; i++) {
			dados.add(Arrays.asList("ORD-" + i, LocalDate.of(2026, 10, 1).plusDays(i % 20), i % 3 == 0 ? "Cancelado" : "Confirmado",
					new BigDecimal(i % 4 == 0 ? "-12.50" : "100.00")));
		}
		SecaoRelatorio ordens = new SecaoRelatorio("Ordens", List.of(
				de("Código", TipoColuna.TEXTO), de("Data", TipoColuna.DATA), de("Status", TipoColuna.STATUS),
				de("Comissão", TipoColuna.DECIMAL).somada()), dados);
		SecaoRelatorio outra = new SecaoRelatorio("Por moeda", List.of(de("Moeda", TipoColuna.TEXTO),
				de("Volume", TipoColuna.DECIMAL).somada()), List.of(Arrays.asList("USD", new BigDecimal("10"))));
		return new RelatorioTabular("Relatório Visual", "visual", List.of("Período: 01/10/2026 a 31/10/2026"),
				List.of(new Destaque("Comissão total", new BigDecimal("1234.56"), TipoColuna.DECIMAL),
						new Destaque("Ordens", linhas, TipoColuna.INTEIRO)),
				List.of(ordens, outra), umaAba);
	}

	private static XSSFWorkbook excel(RelatorioTabular relatorio) throws Exception {
		return new XSSFWorkbook(new ByteArrayInputStream(RelatorioExcelExporter.gerar(relatorio)));
	}

	private static Row linhaQueComecaCom(Sheet aba, String texto) {
		for (Row linha : aba) {
			Cell celula = linha.getCell(0);
			if (celula != null && celula.getCellType() == CellType.STRING && texto.equals(celula.getStringCellValue())) {
				return linha;
			}
		}
		throw new AssertionError("linha começando com '" + texto + "' não encontrada em " + aba.getSheetName());
	}

	private static byte[] rgbDoFundo(Cell celula) {
		XSSFColor cor = ((XSSFCellStyle) celula.getCellStyle()).getFillForegroundColorColor();
		return cor == null ? null : cor.getRGB();
	}

	// ------------------------------------------------------------------ Excel

	@Test
	void excelTemFaixaDeTituloNaCorDaMarcaOcupandoATabelaToda() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet aba = planilha.getSheet("Ordens");
			Cell titulo = aba.getRow(0).getCell(0);
			assertTrue(titulo.getStringCellValue().contains("Relatório Visual"));
			assertArrayEquals(COR_MARCA, rgbDoFundo(titulo));
			boolean mesclada = false;
			for (CellRangeAddress regiao : aba.getMergedRegions()) {
				mesclada |= regiao.getFirstRow() == 0 && regiao.getFirstColumn() == 0 && regiao.getLastColumn() >= 3;
			}
			assertTrue(mesclada, "título deveria ocupar a largura da tabela");
		}
	}

	@Test
	void excelCabecalhoDaTabelaEmNegritoBrancoSobreACorDaMarca() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Cell cabecalho = linhaQueComecaCom(planilha.getSheet("Ordens"), "Código").getCell(0);
			XSSFFont fonte = ((XSSFCellStyle) cabecalho.getCellStyle()).getFont();
			assertTrue(fonte.getBold());
			assertArrayEquals(new byte[] { (byte) 0xFF, (byte) 0xFF, (byte) 0xFF }, fonte.getXSSFColor().getRGB());
			assertArrayEquals(COR_MARCA, rgbDoFundo(cabecalho));
		}
	}

	@Test
	void excelAlternaACorDasLinhasPraGuiarOOlho() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet aba = planilha.getSheet("Ordens");
			int cabecalho = linhaQueComecaCom(aba, "Código").getRowNum();
			byte[] primeira = rgbDoFundo(aba.getRow(cabecalho + 1).getCell(0));
			byte[] segunda = rgbDoFundo(aba.getRow(cabecalho + 2).getCell(0));
			assertFalse(Arrays.equals(primeira, segunda), "linhas vizinhas deveriam ter fundos diferentes");
		}
	}

	@Test
	void excelTemLinhaDeTotalQueAcompanhaOFiltro() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet aba = planilha.getSheet("Ordens");
			Row total = linhaQueComecaCom(aba, "Total");
			Cell soma = total.getCell(3);
			assertEquals(CellType.FORMULA, soma.getCellType());
			// SUBTOTAL(109, ...) soma só as linhas visíveis: filtrar Status = Confirmado no
			// Excel mostra o total só das confirmadas. SUM somaria as ocultas também.
			assertTrue(soma.getCellFormula().startsWith("SUBTOTAL(109,"), soma.getCellFormula());
			assertTrue(((XSSFCellStyle) soma.getCellStyle()).getFont().getBold());
			// coluna sem total fica vazia na linha de total
			assertTrue(total.getCell(1) == null || total.getCell(1).getCellType() == CellType.BLANK);
		}
	}

	@Test
	void excelMostraNegativosEmVermelho() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet aba = planilha.getSheet("Ordens");
			Cell valor = aba.getRow(linhaQueComecaCom(aba, "Código").getRowNum() + 1).getCell(3);
			assertTrue(valor.getCellStyle().getDataFormatString().contains("[Red]"),
					valor.getCellStyle().getDataFormatString());
		}
	}

	@Test
	void excelSemGradeCinzaEProntoPraImprimirEmPaisagem() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet aba = planilha.getSheet("Ordens");
			assertFalse(aba.isDisplayGridlines());
			assertTrue(aba.getPrintSetup().getLandscape());
			assertEquals(1, aba.getPrintSetup().getFitWidth());
			assertNotNull(aba.getRepeatingRows(), "o cabeçalho deveria repetir em cada página impressa");
		}
	}

	@Test
	void excelTrazOsCartoesDeDestaqueNaPrimeiraAba() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, false))) {
			Sheet primeira = planilha.getSheetAt(0);
			boolean rotulo = false;
			boolean valor = false;
			for (Row linha : primeira) {
				for (Cell celula : linha) {
					rotulo |= celula.getCellType() == CellType.STRING && celula.getStringCellValue().equals("Comissão total");
					valor |= celula.getCellType() == CellType.NUMERIC && Math.abs(celula.getNumericCellValue() - 1234.56) < 0.001;
				}
			}
			assertTrue(rotulo && valor, "cartão 'Comissão total' com o valor numérico");
		}
	}

	@Test
	void excelEmUmaAbaEmpilhaAsSecoes() throws Exception {
		try (XSSFWorkbook planilha = excel(relatorio(5, true))) {
			assertEquals(1, planilha.getNumberOfSheets());
			Sheet aba = planilha.getSheetAt(0);
			linhaQueComecaCom(aba, "Ordens");
			linhaQueComecaCom(aba, "Por moeda");
		}
	}

	// ------------------------------------------------------------------ PDF

	private static List<String> paginasDoPdf(RelatorioTabular relatorio) throws Exception {
		PdfReader leitor = new PdfReader(RelatorioPdfExporter.gerar(relatorio));
		PdfTextExtractor extrator = new PdfTextExtractor(leitor);
		List<String> paginas = new ArrayList<>();
		for (int i = 1; i <= leitor.getNumberOfPages(); i++) {
			paginas.add(extrator.getTextFromPage(i).replaceAll("\\s+", " "));
		}
		return paginas;
	}

	@Test
	void pdfNumeraTodasAsPaginasComOTotal() throws Exception {
		List<String> paginas = paginasDoPdf(relatorio(150, false));
		int total = paginas.size();
		assertTrue(total > 1, "precisa de várias páginas pra testar a numeração");
		assertTrue(paginas.get(0).contains("Página 1 de " + total), paginas.get(0));
		assertTrue(paginas.get(total - 1).contains("Página " + total + " de " + total), paginas.get(total - 1));
		assertTrue(paginas.get(total - 1).contains("CambIA"), "rodapé com a marca");
	}

	@Test
	void pdfTrazCartoesDeDestaqueETotal() throws Exception {
		String texto = String.join(" ", paginasDoPdf(relatorio(5, false)));
		assertTrue(texto.contains("Comissão total"), texto);
		assertTrue(texto.contains("1.234,56"), texto);
		assertTrue(texto.contains("Total"), texto);
		// total da coluna Comissão: 5 linhas, 2 com -12,50 (i=0 e i=4) e 3 com 100,00
		assertTrue(texto.contains("275,00"), texto);
	}

	@Test
	void pdfNaoQuebraTituloDeColunaEstreitaMesmoComTextoLongoAoLado() throws Exception {
		// Caso real (Relatório de Ordens renderizado): com nomes de cliente/banco longos, as
		// colunas de texto tomavam o espaço e "Fundo"/"Moeda" quebravam em "Fund o"/"Moed a".
		List<ColunaRelatorio> colunas = new ArrayList<>();
		for (String titulo : List.of("Código da Ordem", "Data do Fechamento", "Cliente", "Banco", "C/V", "Tipo Ordem",
				"Fundo", "Moeda", "Valor em moeda", "Status", "Valor em Real", "Valor Absoluto", "Comissão Líquida",
				"Criado por", "Completado por")) {
			colunas.add(de(titulo, TipoColuna.TEXTO));
		}
		List<List<Object>> linhas = new ArrayList<>();
		for (int i = 0; i < 10; i++) {
			linhas.add(Arrays.asList("2026-000038", "22/10/2026", "Global Trading Exportação e Importação LTDA",
					"Banco Bradesco Financiamentos S.A.", "V", "Credito", "M", "USD", "1.000,00", "Em andamento",
					"5.200,00", "100,00", "66,75", "Integração Beyond Sistemas", "Integração Beyond Sistemas"));
		}
		RelatorioTabular relatorio = new RelatorioTabular("Largo", "largo", List.of(),
				List.of(new SecaoRelatorio("Ordens", colunas, linhas)));

		String texto = String.join(" ", paginasDoPdf(relatorio));
		assertTrue(texto.contains(" Fundo "), texto);
		assertTrue(texto.contains(" Moeda "), texto);
		assertTrue(texto.contains(" C/V "), texto);
		assertTrue(texto.contains(" Status "), texto);
	}

	@Test
	void pdfRepeteOCabecalhoDaTabelaNasPaginasSeguintes() throws Exception {
		List<String> paginas = paginasDoPdf(relatorio(150, false));
		assertTrue(paginas.get(1).contains("Código Data Status Comissão"), paginas.get(1));
		assertNotEquals(paginas.get(0), paginas.get(1));
	}

}

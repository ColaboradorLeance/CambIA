package com.cambia.operacao;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormat;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Planilha pensada para ser lida por alguém que não abriu o sistema: toda tabela tem
 * cabeçalho de coluna, valores em R$ têm formato numérico (não texto), e os rótulos
 * técnicos (status, C/V) aparecem por extenso.
 */
class FechamentoExcelExporter {

	private static final int COLUNAS = 4;

	private static final Map<String, String> ROTULOS_STATUS = Map.of(
			"ANDAMENTO", "Em andamento",
			"CONFIRMADO", "Confirmado",
			"CANCELADO", "Cancelado");

	private static final Map<String, String> ROTULOS_TIPO = Map.of(
			"C", "C (Compra)",
			"V", "V (Venda)");

	static byte[] gerar(FechamentoResponse fechamento) {
		try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			Sheet sheet = workbook.createSheet("Fechamento " + fechamento.data());
			Estilos estilos = new Estilos(workbook);
			int[] linha = { 0 };

			escreverTitulo(sheet, linha, "Fechamento Diário — " + fechamento.data(), estilos);
			escreverSubtitulo(sheet, linha, "Gerado em "
					+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm")), estilos);
			linha[0]++;

			escreverSecao(sheet, linha, "Resumo operacional", estilos);
			escreverValorTexto(sheet, linha, "Total de operações", fechamento.resumoOperacional().totalOperacoes(), estilos);
			escreverCabecalho(sheet, linha, estilos, "Status", "Quantidade");
			for (Map.Entry<String, Integer> entrada : new TreeMap<>(fechamento.resumoOperacional().porStatus()).entrySet()) {
				escreverLinhaTextoInteiro(sheet, linha,
						ROTULOS_STATUS.getOrDefault(entrada.getKey(), entrada.getKey()), entrada.getValue(), estilos);
			}
			linha[0]++;

			escreverSecao(sheet, linha, "Resultado financeiro (só operações completas)", estilos);
			escreverValorTexto(sheet, linha, "Quantidade de operações",
					fechamento.resultadoFinanceiro().quantidadeOperacoes(), estilos);
			escreverCabecalho(sheet, linha, estilos, "Métrica", "Valor (R$)");
			escreverLinhaTextoValor(sheet, linha, "Total R$", fechamento.resultadoFinanceiro().totalReais(), estilos);
			escreverLinhaTextoValor(sheet, linha, "Total Bruto do Câmbio",
					fechamento.resultadoFinanceiro().totalBrutoCambio(), estilos);
			escreverLinhaTextoValor(sheet, linha, "Comissão Líquida",
					fechamento.resultadoFinanceiro().totalComissaoLiquida(), estilos);
			escreverLinhaTextoValor(sheet, linha, "Ticket médio", fechamento.resultadoFinanceiro().ticketMedioReais(),
					estilos);
			escreverLinhaTextoValor(sheet, linha, "Maior operação",
					fechamento.resultadoFinanceiro().maiorOperacaoReais(), estilos);
			escreverLinhaTextoValor(sheet, linha, "Menor operação",
					fechamento.resultadoFinanceiro().menorOperacaoReais(), estilos);
			linha[0]++;

			escreverTabelaVolumePorMoeda(sheet, linha, "Volume por moeda (ME)", "Volume (ME)",
					fechamento.resultadoFinanceiro().volumePorMoeda(),
					fechamento.resultadoFinanceiro().quantidadePorMoeda(), estilos);

			escreverQuebra(sheet, linha, "Quebra por banco", "Banco", fechamento.quebras().porBanco(), estilos, null);
			escreverQuebra(sheet, linha, "Quebra por cliente", "Cliente", fechamento.quebras().porCliente(), estilos, null);
			escreverQuebra(sheet, linha, "Quebra por moeda", "Moeda", fechamento.quebras().porMoeda(), estilos, null);
			escreverQuebra(sheet, linha, "Quebra por tipo (C/V)", "Tipo", fechamento.quebras().porTipo(), estilos,
					ROTULOS_TIPO);

			escreverSecao(sheet, linha, "Posição em aberto (acumulada, todas as datas)", estilos);
			escreverValorTexto(sheet, linha, "Operações em andamento",
					fechamento.posicaoEmAberto().totalOperacoesEmAndamento(), estilos);
			linha[0]++;
			escreverTabelaPorMoeda(sheet, linha, "Exposição por moeda (valor em ME)", "Exposição (ME)",
					fechamento.posicaoEmAberto().exposicaoPorMoeda(), estilos);

			for (int coluna = 0; coluna < COLUNAS; coluna++) {
				sheet.autoSizeColumn(coluna);
			}

			workbook.write(saida);
			return saida.toByteArray();
		} catch (IOException e) {
			throw new UncheckedIOException("Falha ao gerar Excel do fechamento", e);
		}
	}

	private static void escreverTitulo(Sheet sheet, int[] linha, String texto, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell cell = row.createCell(0);
		cell.setCellValue(texto);
		cell.setCellStyle(estilos.titulo);
		sheet.addMergedRegion(new CellRangeAddress(row.getRowNum(), row.getRowNum(), 0, COLUNAS - 1));
	}

	private static void escreverSubtitulo(Sheet sheet, int[] linha, String texto, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell cell = row.createCell(0);
		cell.setCellValue(texto);
		cell.setCellStyle(estilos.subtitulo);
	}

	private static void escreverSecao(Sheet sheet, int[] linha, String texto, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		for (int coluna = 0; coluna < COLUNAS; coluna++) {
			Cell cell = row.createCell(coluna);
			cell.setCellStyle(estilos.secao);
			if (coluna == 0) {
				cell.setCellValue(texto);
			}
		}
	}

	private static void escreverCabecalho(Sheet sheet, int[] linha, Estilos estilos, String... colunas) {
		Row row = sheet.createRow(linha[0]++);
		for (int i = 0; i < colunas.length; i++) {
			Cell cell = row.createCell(i);
			cell.setCellValue(colunas[i]);
			cell.setCellStyle(estilos.cabecalhoTabela);
		}
	}

	private static void escreverValorTexto(Sheet sheet, int[] linha, String rotulo, int valor, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell rotuloCell = row.createCell(0);
		rotuloCell.setCellValue(rotulo);
		rotuloCell.setCellStyle(estilos.rotuloDestaque);
		Cell valorCell = row.createCell(1);
		valorCell.setCellValue(valor);
		valorCell.setCellStyle(estilos.numeroInteiroDestaque);
	}

	private static void escreverLinhaTextoInteiro(Sheet sheet, int[] linha, String rotulo, int valor, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell rotuloCell = row.createCell(0);
		rotuloCell.setCellValue(rotulo);
		rotuloCell.setCellStyle(estilos.celulaTexto);
		Cell valorCell = row.createCell(1);
		valorCell.setCellValue(valor);
		valorCell.setCellStyle(estilos.numeroInteiro);
	}

	private static void escreverLinhaTextoValor(Sheet sheet, int[] linha, String rotulo, BigDecimal valor,
			Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell rotuloCell = row.createCell(0);
		rotuloCell.setCellValue(rotulo);
		rotuloCell.setCellStyle(estilos.celulaTexto);
		Cell valorCell = row.createCell(1);
		valorCell.setCellValue(valor(valor));
		valorCell.setCellStyle(estilos.numeroDecimal);
	}

	private static void escreverTabelaPorMoeda(Sheet sheet, int[] linha, String titulo, String rotuloColunaValor,
			Map<String, BigDecimal> porMoeda, Estilos estilos) {
		if (porMoeda.isEmpty()) {
			return;
		}
		escreverSubsecao(sheet, linha, titulo, estilos);
		escreverCabecalho(sheet, linha, estilos, "Moeda", rotuloColunaValor);
		for (Map.Entry<String, BigDecimal> entrada : new TreeMap<>(porMoeda).entrySet()) {
			Row row = sheet.createRow(linha[0]++);
			Cell moedaCell = row.createCell(0);
			moedaCell.setCellValue(entrada.getKey());
			moedaCell.setCellStyle(estilos.celulaTexto);
			Cell valorCell = row.createCell(1);
			valorCell.setCellValue(valor(entrada.getValue()));
			valorCell.setCellStyle(estilos.numeroDecimal);
		}
		linha[0]++;
	}

	// Igual escreverTabelaPorMoeda, mas com uma coluna a mais de "Quantidade" — só usado no
	// Volume por moeda do Resultado Financeiro (Exposição por moeda não tem essa contagem).
	private static void escreverTabelaVolumePorMoeda(Sheet sheet, int[] linha, String titulo, String rotuloColunaValor,
			Map<String, BigDecimal> porMoeda, Map<String, Integer> quantidadePorMoeda, Estilos estilos) {
		if (porMoeda.isEmpty()) {
			return;
		}
		escreverSubsecao(sheet, linha, titulo, estilos);
		escreverCabecalho(sheet, linha, estilos, "Moeda", "Quantidade", rotuloColunaValor);
		for (Map.Entry<String, BigDecimal> entrada : new TreeMap<>(porMoeda).entrySet()) {
			Row row = sheet.createRow(linha[0]++);
			Cell moedaCell = row.createCell(0);
			moedaCell.setCellValue(entrada.getKey());
			moedaCell.setCellStyle(estilos.celulaTexto);

			Cell quantidadeCell = row.createCell(1);
			quantidadeCell.setCellValue(quantidadePorMoeda.getOrDefault(entrada.getKey(), 0));
			quantidadeCell.setCellStyle(estilos.numeroInteiro);

			Cell valorCell = row.createCell(2);
			valorCell.setCellValue(valor(entrada.getValue()));
			valorCell.setCellStyle(estilos.numeroDecimal);
		}
		linha[0]++;
	}

	private static void escreverSubsecao(Sheet sheet, int[] linha, String texto, Estilos estilos) {
		Row row = sheet.createRow(linha[0]++);
		Cell cell = row.createCell(0);
		cell.setCellValue(texto);
		cell.setCellStyle(estilos.subsecao);
	}

	private static void escreverQuebra(Sheet sheet, int[] linha, String titulo, String rotuloPrimeiraColuna,
			List<QuebraItem> itens, Estilos estilos, Map<String, String> traducaoRotulo) {
		escreverSubsecao(sheet, linha, titulo, estilos);
		escreverCabecalho(sheet, linha, estilos, rotuloPrimeiraColuna, "Quantidade", "Total R$", "Comissão Líquida (R$)");

		if (itens.isEmpty()) {
			Row row = sheet.createRow(linha[0]++);
			Cell cell = row.createCell(0);
			cell.setCellValue("Nenhuma operação nesta dimensão");
			cell.setCellStyle(estilos.celulaTexto);
		}

		for (QuebraItem item : itens) {
			Row row = sheet.createRow(linha[0]++);
			Cell rotuloCell = row.createCell(0);
			rotuloCell.setCellValue(traducaoRotulo == null ? item.rotulo()
					: traducaoRotulo.getOrDefault(item.rotulo(), item.rotulo()));
			rotuloCell.setCellStyle(estilos.celulaTexto);

			Cell quantidadeCell = row.createCell(1);
			quantidadeCell.setCellValue(item.quantidade());
			quantidadeCell.setCellStyle(estilos.numeroInteiro);

			Cell reaisCell = row.createCell(2);
			reaisCell.setCellValue(valor(item.totalReais()));
			reaisCell.setCellStyle(estilos.numeroDecimal);

			Cell comissaoCell = row.createCell(3);
			comissaoCell.setCellValue(valor(item.totalComissaoLiquida()));
			comissaoCell.setCellStyle(estilos.numeroDecimal);
		}
		linha[0]++;
	}

	private static double valor(BigDecimal valor) {
		return valor == null ? 0 : valor.doubleValue();
	}

	private static class Estilos {
		final CellStyle titulo;
		final CellStyle subtitulo;
		final CellStyle secao;
		final CellStyle subsecao;
		final CellStyle cabecalhoTabela;
		final CellStyle celulaTexto;
		final CellStyle rotuloDestaque;
		final CellStyle numeroInteiro;
		final CellStyle numeroDecimal;
		final CellStyle numeroInteiroDestaque;

		Estilos(XSSFWorkbook workbook) {
			DataFormat formato = workbook.createDataFormat();
			short formatoInteiro = formato.getFormat("#,##0");
			short formatoDecimal = formato.getFormat("#,##0.00");

			Font fonteTitulo = workbook.createFont();
			fonteTitulo.setBold(true);
			fonteTitulo.setFontHeightInPoints((short) 14);

			Font fonteSubtitulo = workbook.createFont();
			fonteSubtitulo.setItalic(true);
			fonteSubtitulo.setColor(IndexedColors.GREY_50_PERCENT.getIndex());

			Font fonteSecao = workbook.createFont();
			fonteSecao.setBold(true);
			fonteSecao.setColor(IndexedColors.WHITE.getIndex());

			Font fonteSubsecao = workbook.createFont();
			fonteSubsecao.setBold(true);

			Font fonteCabecalho = workbook.createFont();
			fonteCabecalho.setBold(true);

			Font fonteDestaque = workbook.createFont();
			fonteDestaque.setBold(true);

			titulo = workbook.createCellStyle();
			titulo.setFont(fonteTitulo);

			subtitulo = workbook.createCellStyle();
			subtitulo.setFont(fonteSubtitulo);

			secao = workbook.createCellStyle();
			secao.setFont(fonteSecao);
			secao.setFillForegroundColor(IndexedColors.DARK_BLUE.getIndex());
			secao.setFillPattern(FillPatternType.SOLID_FOREGROUND);

			subsecao = workbook.createCellStyle();
			subsecao.setFont(fonteSubsecao);

			cabecalhoTabela = workbook.createCellStyle();
			cabecalhoTabela.setFont(fonteCabecalho);
			cabecalhoTabela.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
			cabecalhoTabela.setFillPattern(FillPatternType.SOLID_FOREGROUND);
			bordaFina(cabecalhoTabela);

			celulaTexto = workbook.createCellStyle();
			bordaFina(celulaTexto);

			rotuloDestaque = workbook.createCellStyle();
			rotuloDestaque.setFont(fonteDestaque);
			bordaFina(rotuloDestaque);

			numeroInteiro = workbook.createCellStyle();
			numeroInteiro.setDataFormat(formatoInteiro);
			bordaFina(numeroInteiro);

			numeroDecimal = workbook.createCellStyle();
			numeroDecimal.setDataFormat(formatoDecimal);
			bordaFina(numeroDecimal);

			numeroInteiroDestaque = workbook.createCellStyle();
			numeroInteiroDestaque.setDataFormat(formatoInteiro);
			numeroInteiroDestaque.setFont(fonteDestaque);
			bordaFina(numeroInteiroDestaque);
		}

		private static void bordaFina(CellStyle estilo) {
			estilo.setBorderTop(BorderStyle.THIN);
			estilo.setBorderBottom(BorderStyle.THIN);
			estilo.setBorderLeft(BorderStyle.THIN);
			estilo.setBorderRight(BorderStyle.THIN);
		}
	}

}

package com.cambia.operacao;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

class FechamentoPdfExporter {

	private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
	private static final Font SECAO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
	private static final Font NORMAL = FontFactory.getFont(FontFactory.HELVETICA, 10);

	static byte[] gerar(FechamentoResponse fechamento) {
		try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
			Document documento = new Document();
			PdfWriter.getInstance(documento, saida);
			documento.open();

			documento.add(new Paragraph("Fechamento Diário — " + fechamento.data(), TITULO));
			documento.add(new Paragraph(" "));

			documento.add(new Paragraph("Resumo operacional", SECAO));
			documento.add(paragrafo("Total de operações: " + fechamento.resumoOperacional().totalOperacoes()));
			for (Map.Entry<String, Integer> entrada : fechamento.resumoOperacional().porStatus().entrySet()) {
				documento.add(paragrafo(entrada.getKey() + ": " + entrada.getValue()));
			}
			documento.add(new Paragraph(" "));

			documento.add(new Paragraph("Resultado financeiro (só operações completas)", SECAO));
			documento.add(paragrafo("Quantidade de operações: " + fechamento.resultadoFinanceiro().quantidadeOperacoes()));
			documento.add(paragrafo("Total R$: " + fmt(fechamento.resultadoFinanceiro().totalReais())));
			documento.add(paragrafo("Total Bruto do Câmbio: " + fmt(fechamento.resultadoFinanceiro().totalBrutoCambio())));
			documento.add(paragrafo("Comissão Líquida: " + fmt(fechamento.resultadoFinanceiro().totalComissaoLiquida())));
			documento.add(paragrafo("Ticket médio: " + fmt(fechamento.resultadoFinanceiro().ticketMedioReais())));
			documento.add(paragrafo("Maior operação: " + fmt(fechamento.resultadoFinanceiro().maiorOperacaoReais())));
			documento.add(paragrafo("Menor operação: " + fmt(fechamento.resultadoFinanceiro().menorOperacaoReais())));
			Map<String, Integer> quantidadePorMoeda = fechamento.resultadoFinanceiro().quantidadePorMoeda();
			for (Map.Entry<String, BigDecimal> entrada : fechamento.resultadoFinanceiro().volumePorMoeda().entrySet()) {
				int quantidade = quantidadePorMoeda.getOrDefault(entrada.getKey(), 0);
				documento.add(paragrafo("Volume " + entrada.getKey() + ": " + fmt(entrada.getValue())
						+ " (" + quantidade + (quantidade == 1 ? " ordem" : " ordens") + ")"));
			}
			documento.add(new Paragraph(" "));

			adicionarQuebra(documento, "Por banco", fechamento.quebras().porBanco());
			adicionarQuebra(documento, "Por cliente", fechamento.quebras().porCliente());
			adicionarQuebra(documento, "Por moeda", fechamento.quebras().porMoeda());
			adicionarQuebra(documento, "Por tipo (C/V)", fechamento.quebras().porTipo());

			documento.add(new Paragraph("Posição em aberto (acumulada)", SECAO));
			documento.add(paragrafo(
					"Operações em andamento: " + fechamento.posicaoEmAberto().totalOperacoesEmAndamento()));
			for (Map.Entry<String, BigDecimal> entrada : fechamento.posicaoEmAberto().exposicaoPorMoeda().entrySet()) {
				documento.add(paragrafo("Exposição " + entrada.getKey() + ": " + fmt(entrada.getValue())));
			}

			documento.close();
			return saida.toByteArray();
		} catch (DocumentException | java.io.IOException e) {
			throw new RuntimeException("Falha ao gerar PDF do fechamento", e);
		}
	}

	private static void adicionarQuebra(Document documento, String titulo, List<QuebraItem> itens)
			throws DocumentException {
		documento.add(new Paragraph(titulo, SECAO));
		if (itens.isEmpty()) {
			documento.add(paragrafo("Nenhuma operação."));
			documento.add(new Paragraph(" "));
			return;
		}

		PdfPTable tabela = new PdfPTable(4);
		tabela.setWidthPercentage(100);
		tabela.addCell(celulaCabecalho(""));
		tabela.addCell(celulaCabecalho("Qtd."));
		tabela.addCell(celulaCabecalho("Total R$"));
		tabela.addCell(celulaCabecalho("Comissão R$"));

		for (QuebraItem item : itens) {
			tabela.addCell(new PdfPCell(new Paragraph(item.rotulo(), NORMAL)));
			tabela.addCell(new PdfPCell(new Paragraph(String.valueOf(item.quantidade()), NORMAL)));
			tabela.addCell(new PdfPCell(new Paragraph(fmt(item.totalReais()), NORMAL)));
			tabela.addCell(new PdfPCell(new Paragraph(fmt(item.totalComissaoLiquida()), NORMAL)));
		}

		documento.add(tabela);
		documento.add(new Paragraph(" "));
	}

	private static PdfPCell celulaCabecalho(String texto) {
		return new PdfPCell(new Paragraph(texto, SECAO));
	}

	private static Paragraph paragrafo(String texto) {
		return new Paragraph(texto, NORMAL);
	}

	private static String fmt(BigDecimal valor) {
		return valor == null ? "—" : valor.toPlainString();
	}

}

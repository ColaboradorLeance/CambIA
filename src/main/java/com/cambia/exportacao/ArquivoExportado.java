package com.cambia.exportacao;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Resposta HTTP de download, no formato pedido pela URL ({@code pdf} ou {@code xlsx}). */
public final class ArquivoExportado {

	public static final String FORMATOS = "pdf|xlsx";
	private static final MediaType XLSX = MediaType
			.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

	private ArquivoExportado() {
	}

	public static ResponseEntity<byte[]> resposta(RelatorioTabular relatorio, String formato) {
		boolean pdf = "pdf".equals(formato);
		byte[] conteudo = pdf ? RelatorioPdfExporter.gerar(relatorio) : RelatorioExcelExporter.gerar(relatorio);
		String nome = relatorio.nomeArquivo() + (pdf ? ".pdf" : ".xlsx");
		return ResponseEntity.ok()
				.contentType(pdf ? MediaType.APPLICATION_PDF : XLSX)
				.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(nome).build().toString())
				.body(conteudo);
	}

}

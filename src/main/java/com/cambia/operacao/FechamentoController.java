package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fechamentos")
class FechamentoController {

	private final FechamentoService service;
	private final FechamentoConfiguracaoService configuracaoService;

	FechamentoController(FechamentoService service, FechamentoConfiguracaoService configuracaoService) {
		this.service = service;
		this.configuracaoService = configuracaoService;
	}

	@GetMapping("/configuracao")
	ConfiguracaoFechamentoResponse obterConfiguracao() {
		return configuracaoService.obter();
	}

	@PutMapping("/configuracao")
	ConfiguracaoFechamentoResponse definirConfiguracao(@Valid @RequestBody ConfiguracaoFechamentoRequest request) {
		return configuracaoService.definir(request.horaExecucao());
	}

	@GetMapping("/historico")
	List<FechamentoHistoricoItem> historico() {
		return configuracaoService.listarHistorico();
	}

	@GetMapping("/destinatarios")
	List<DestinatarioFechamentoResponse> destinatarios() {
		return configuracaoService.listarDestinatarios();
	}

	@PutMapping("/destinatarios")
	List<DestinatarioFechamentoResponse> definirDestinatarios(
			@Valid @RequestBody DestinatariosFechamentoRequest request) {
		return configuracaoService.definirDestinatarios(request.usuarioIds());
	}

	@GetMapping("/historico/{id}/download")
	ResponseEntity<byte[]> baixarHistorico(@PathVariable Long id) {
		FechamentoGerado gerado = configuracaoService.buscarGerado(id);
		MediaType tipo = "PDF".equals(gerado.getFormato()) ? MediaType.APPLICATION_PDF
				: MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
		return ResponseEntity.ok()
				.contentType(tipo)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + gerado.getNomeArquivo() + "\"")
				.body(gerado.getConteudo());
	}

	@GetMapping("/{data}")
	FechamentoResponse calcular(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		return service.calcular(data);
	}

	@GetMapping("/{data}/pdf")
	ResponseEntity<byte[]> exportarPdf(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		byte[] pdf = FechamentoPdfExporter.gerar(service.calcular(data));
		return ResponseEntity.ok()
				.contentType(MediaType.APPLICATION_PDF)
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fechamento-" + data + ".pdf\"")
				.body(pdf);
	}

	@GetMapping("/{data}/xlsx")
	ResponseEntity<byte[]> exportarExcel(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {
		byte[] excel = FechamentoExcelExporter.gerar(service.calcular(data));
		return ResponseEntity.ok()
				.contentType(MediaType.parseMediaType(
						"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fechamento-" + data + ".xlsx\"")
				.body(excel);
	}

}

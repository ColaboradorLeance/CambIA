package com.cambia.operacao;

import com.cambia.chaveapi.ChaveApiNaRequisicao;
import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import com.cambia.usuario.Usuario;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operacoes")
class OperacaoController {

	private final OperacaoService service;
	private final OperacaoPaginacaoService paginacaoService;

	OperacaoController(OperacaoService service, OperacaoPaginacaoService paginacaoService) {
		this.service = service;
		this.paginacaoService = paginacaoService;
	}

	@PostMapping
	ResponseEntity<OperacaoResponse> criar(@Valid @RequestBody OperacaoRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		// Só a criação pode vir de chave de API (K4: nenhum escopo edita, confirma ou cancela).
		Operacao operacao = service.criar(request, usuarioLogado.getId(), ChaveApiNaRequisicao.idAtual());
		return ResponseEntity.created(URI.create("/operacoes/" + operacao.getId()))
				.body(service.toResponse(operacao));
	}

	@GetMapping("/historico")
	List<OperacaoEventoResponse> historico() {
		return service.historico();
	}

	@GetMapping("/{id}")
	OperacaoResponse buscar(@PathVariable Long id) {
		return service.toResponse(service.buscar(id));
	}

	@PutMapping("/{id}")
	OperacaoResponse editar(@PathVariable Long id, @Valid @RequestBody OperacaoRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		return service.toResponse(service.editar(id, request, usuarioLogado.getId()));
	}

	// Sem parâmetro, continua trazendo todas (compatibilidade com integrações); as telas
	// usam ?status= (Em andamento) ou /pagina (Confirmadas, Painel) — Incremento 80.
	@GetMapping
	List<OperacaoResponse> listar(@RequestParam(required = false) StatusOperacao status) {
		return service.toResponses(status == null ? service.listar() : service.listarPorStatus(status));
	}

	@GetMapping("/pagina")
	PaginaResponse<OperacaoResponse> pagina(FiltroOperacoes filtro, @RequestParam(defaultValue = "0") int pagina,
			@RequestParam(defaultValue = "50") int tamanho) {
		return paginacaoService.paginar(filtro, pagina, tamanho);
	}

	@GetMapping("/opcoes-filtro")
	OpcoesFiltroResponse opcoesFiltro(@RequestParam StatusOperacao status) {
		return paginacaoService.opcoesDeFiltro(status);
	}

	@PatchMapping("/{id}/status")
	OperacaoResponse atualizarStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		return service.toResponse(service.atualizarStatus(id, request.status(), usuarioLogado.getId()));
	}

}

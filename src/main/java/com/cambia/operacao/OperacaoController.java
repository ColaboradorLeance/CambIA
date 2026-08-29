package com.cambia.operacao;

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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/operacoes")
class OperacaoController {

	private final OperacaoService service;

	OperacaoController(OperacaoService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<OperacaoResponse> criar(@Valid @RequestBody OperacaoRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		Operacao operacao = service.criar(request, usuarioLogado.getId());
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

	@GetMapping
	List<OperacaoResponse> listar() {
		return service.listar().stream().map(service::toResponse).toList();
	}

	@PatchMapping("/{id}/status")
	OperacaoResponse atualizarStatus(@PathVariable Long id, @Valid @RequestBody StatusUpdateRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		return service.toResponse(service.atualizarStatus(id, request.status(), usuarioLogado.getId()));
	}

}

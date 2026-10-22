package com.cambia.chaveapi;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import com.cambia.usuario.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Só Admin (D1) — a restrição fica no SecurityConfig, junto das demais regras de acesso. */
@RestController
@RequestMapping("/chaves-api")
class ChaveApiController {

	private final ChaveApiService service;

	ChaveApiController(ChaveApiService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ChaveApiCriadaResponse> criar(@Valid @RequestBody ChaveApiRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		ChaveApiCriadaResponse criada = service.criar(request, usuarioLogado.getId());
		return ResponseEntity.created(URI.create("/chaves-api/" + criada.id())).body(criada);
	}

	@GetMapping
	List<ChaveApiResponse> listar() {
		return service.listar();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revogar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuarioLogado) {
		service.revogar(id, usuarioLogado.getId());
	}

}

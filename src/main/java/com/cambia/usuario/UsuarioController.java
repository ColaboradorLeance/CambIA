package com.cambia.usuario;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/usuarios")
class UsuarioController {

	private final UsuarioService service;

	UsuarioController(UsuarioService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<UsuarioResponse> criar(@Valid @RequestBody UsuarioRequest request) {
		Usuario usuario = service.criar(request);
		return ResponseEntity.created(URI.create("/usuarios/" + usuario.getId()))
				.body(UsuarioResponse.from(usuario));
	}

	@GetMapping("/{id}")
	UsuarioResponse buscar(@PathVariable Long id) {
		return UsuarioResponse.from(service.buscar(id));
	}

	@GetMapping
	List<UsuarioResponse> listar() {
		return service.listar().stream().map(UsuarioResponse::from).toList();
	}

	@PutMapping("/{id}")
	UsuarioResponse atualizar(@PathVariable Long id, @Valid @RequestBody UsuarioRequest request) {
		return UsuarioResponse.from(service.atualizar(id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remover(@PathVariable Long id) {
		service.remover(id);
		return ResponseEntity.noContent().build();
	}

}

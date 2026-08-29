package com.cambia.cliente;

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
@RequestMapping("/clientes")
class ClienteController {

	private final ClienteService service;

	ClienteController(ClienteService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ClienteResponse> criar(@Valid @RequestBody ClienteRequest request) {
		Cliente cliente = service.criar(request);
		return ResponseEntity.created(URI.create("/clientes/" + cliente.getId()))
				.body(ClienteResponse.from(cliente));
	}

	@GetMapping("/{id}")
	ClienteResponse buscar(@PathVariable Long id) {
		return ClienteResponse.from(service.buscar(id));
	}

	@GetMapping
	List<ClienteResponse> listar() {
		return service.listar().stream().map(ClienteResponse::from).toList();
	}

	@PutMapping("/{id}")
	ClienteResponse atualizar(@PathVariable Long id, @Valid @RequestBody ClienteRequest request) {
		return ClienteResponse.from(service.atualizar(id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remover(@PathVariable Long id) {
		service.remover(id);
		return ResponseEntity.noContent().build();
	}

}

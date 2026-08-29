package com.cambia.banco;

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
@RequestMapping("/calculos")
class CalculoController {

	private final CalculoService service;

	CalculoController(CalculoService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<CalculoResponse> criar(@Valid @RequestBody CalculoRequest request) {
		Calculo calculo = service.criar(request);
		return ResponseEntity.created(URI.create("/calculos/" + calculo.getId()))
				.body(CalculoResponse.from(calculo));
	}

	@PostMapping("/teste")
	TesteFormulaResponse testarFormula(@Valid @RequestBody TesteFormulaRequest request) {
		return service.testarFormula(request);
	}

	@GetMapping("/{id}")
	CalculoResponse buscar(@PathVariable Long id) {
		return CalculoResponse.from(service.buscar(id));
	}

	@GetMapping
	List<CalculoResponse> listar() {
		return service.listar().stream().map(CalculoResponse::from).toList();
	}

	@PutMapping("/{id}")
	CalculoResponse atualizar(@PathVariable Long id, @Valid @RequestBody CalculoRequest request) {
		return CalculoResponse.from(service.atualizar(id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remover(@PathVariable Long id) {
		service.remover(id);
		return ResponseEntity.noContent().build();
	}

}

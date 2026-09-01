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
	private final ConsultaCnpjClient consultaCnpjClient;

	ClienteController(ClienteService service, ConsultaCnpjClient consultaCnpjClient) {
		this.service = service;
		this.consultaCnpjClient = consultaCnpjClient;
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

	// Preenchimento automático do Nome a partir do CNPJ no formulário de cadastro
	// (Incremento 42) — só CNPJ (14 dígitos), nunca CPF. Sempre responde 200, mesmo
	// sem achar nada: é um preenchimento de conveniência, nunca deve travar o cadastro
	// manual. Ver ConsultaCnpjClient.
	@GetMapping("/consulta-cnpj/{cnpj}")
	ConsultaCnpjResponse consultarCnpj(@PathVariable String cnpj) {
		String somenteDigitos = cnpj.replaceAll("\\D", "");
		if (somenteDigitos.length() != 14) {
			return new ConsultaCnpjResponse(null);
		}
		return new ConsultaCnpjResponse(consultaCnpjClient.buscarNome(somenteDigitos).orElse(null));
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

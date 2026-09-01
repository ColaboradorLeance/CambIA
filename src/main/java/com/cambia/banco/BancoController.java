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
@RequestMapping("/bancos")
class BancoController {

	private final BancoService service;
	private final ConsultaBancoClient consultaBancoClient;

	BancoController(BancoService service, ConsultaBancoClient consultaBancoClient) {
		this.service = service;
		this.consultaBancoClient = consultaBancoClient;
	}

	@PostMapping
	ResponseEntity<BancoResponse> criar(@Valid @RequestBody BancoRequest request) {
		Banco banco = service.criar(request);
		return ResponseEntity.created(URI.create("/bancos/" + banco.getId()))
				.body(BancoResponse.from(banco));
	}

	@GetMapping("/{id}")
	BancoResponse buscar(@PathVariable Long id) {
		return BancoResponse.from(service.buscar(id));
	}

	@GetMapping
	List<BancoResponse> listar() {
		return service.listar().stream().map(BancoResponse::from).toList();
	}

	// Preenchimento automático do Nome a partir do código COMPE (3 dígitos) ou ISPB
	// (8 dígitos) digitado em "Código do banco" (Incremento 43). Sempre responde 200,
	// mesmo sem achar nada: é um preenchimento de conveniência, nunca deve travar o
	// cadastro manual. Ver ConsultaBancoClient.
	@GetMapping("/consulta-codigo/{codigo}")
	ConsultaBancoResponse consultarCodigo(@PathVariable String codigo) {
		String somenteDigitos = codigo.replaceAll("\\D", "");
		if (somenteDigitos.length() == 3) {
			return new ConsultaBancoResponse(consultaBancoClient.buscarNomePorCompe(somenteDigitos).orElse(null));
		}
		if (somenteDigitos.length() == 8) {
			return new ConsultaBancoResponse(consultaBancoClient.buscarNomePorIspb(somenteDigitos).orElse(null));
		}
		return new ConsultaBancoResponse(null);
	}

	@PutMapping("/{id}")
	BancoResponse atualizar(@PathVariable Long id, @Valid @RequestBody BancoRequest request) {
		return BancoResponse.from(service.atualizar(id, request));
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remover(@PathVariable Long id) {
		service.remover(id);
		return ResponseEntity.noContent().build();
	}

}

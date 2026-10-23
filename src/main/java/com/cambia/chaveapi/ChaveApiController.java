package com.cambia.chaveapi;

import java.util.List;

import com.cambia.usuario.Usuario;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tela do Admin: todas as chaves, com o dono, e revogação de qualquer uma (K7 —
 * emergência: chave vazada de quem saiu ou está ausente). Não cria chave para outro
 * usuário: desde o Incremento 81 cada um cria a sua em /perfil/chaves-api (K1). A
 * restrição a Admin fica no SecurityConfig, junto das demais regras de acesso.
 */
@RestController
@RequestMapping("/chaves-api")
class ChaveApiController {

	private final ChaveApiService service;

	ChaveApiController(ChaveApiService service) {
		this.service = service;
	}

	@GetMapping
	List<ChaveApiResponse> listar() {
		return service.listarTodas();
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revogar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuarioLogado) {
		service.revogarQualquer(id, usuarioLogado.getId());
	}

}

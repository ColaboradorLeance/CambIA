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

/**
 * "Meu perfil → Chaves de API" (Incremento 81, K1): cada usuário, em qualquer perfil, cria,
 * lista e revoga só as próprias chaves — sem depender do Admin. Exige sessão de tela; com
 * chave de API a rota é recusada (D7, no filtro e no SecurityConfig): chave não cria chave.
 */
@RestController
@RequestMapping("/perfil/chaves-api")
class PerfilChaveApiController {

	private final ChaveApiService service;

	PerfilChaveApiController(ChaveApiService service) {
		this.service = service;
	}

	@PostMapping
	ResponseEntity<ChaveApiCriadaResponse> criar(@Valid @RequestBody PerfilChaveApiRequest request,
			@AuthenticationPrincipal Usuario usuarioLogado) {
		ChaveApiCriadaResponse criada = service.criar(request, usuarioLogado);
		return ResponseEntity.created(URI.create("/perfil/chaves-api/" + criada.id())).body(criada);
	}

	@GetMapping
	List<PerfilChaveApiResponse> listar(@AuthenticationPrincipal Usuario usuarioLogado) {
		return service.listarDoUsuario(usuarioLogado.getId());
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void revogar(@PathVariable Long id, @AuthenticationPrincipal Usuario usuarioLogado) {
		service.revogarDoUsuario(id, usuarioLogado.getId());
	}

}

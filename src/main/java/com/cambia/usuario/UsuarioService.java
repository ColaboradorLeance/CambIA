package com.cambia.usuario;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class UsuarioService {

	private final UsuarioRepository repository;

	UsuarioService(UsuarioRepository repository) {
		this.repository = repository;
	}

	Usuario criar(UsuarioRequest request) {
		if (repository.existsByEmail(request.email())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail");
		}
		Usuario usuario = new Usuario(request.nome(), request.email(), request.perfil());
		return repository.save(usuario);
	}

	Usuario buscar(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado"));
	}

	List<Usuario> listar() {
		return repository.findAll();
	}

	Usuario atualizar(Long id, UsuarioRequest request) {
		Usuario usuario = buscar(id);
		repository.findByEmail(request.email())
				.filter(outro -> !outro.getId().equals(id))
				.ifPresent(outro -> {
					throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe um usuário com este e-mail");
				});
		usuario.atualizar(request.nome(), request.email(), request.perfil());
		return repository.save(usuario);
	}

	void remover(Long id) {
		Usuario usuario = buscar(id);
		repository.delete(usuario);
		// flush força o DELETE a ir pro banco agora — se ele violar alguma referência
		// (sessão, operação, etc.), o erro aparece já nesta chamada, com a mensagem clara
		// do TratamentoErroGlobal, em vez de estourar num flush mais tarde e menos óbvio.
		repository.flush();
	}

}

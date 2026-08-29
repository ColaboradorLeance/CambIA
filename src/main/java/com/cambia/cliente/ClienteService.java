package com.cambia.cliente;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class ClienteService {

	private final ClienteRepository repository;

	ClienteService(ClienteRepository repository) {
		this.repository = repository;
	}

	Cliente criar(ClienteRequest request) {
		Cliente cliente = new Cliente(request.nome(), request.documento());
		return repository.save(cliente);
	}

	Cliente buscar(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente não encontrado"));
	}

	List<Cliente> listar() {
		return repository.findAll();
	}

	Cliente atualizar(Long id, ClienteRequest request) {
		Cliente cliente = buscar(id);
		cliente.atualizar(request.nome(), request.documento());
		return repository.save(cliente);
	}

	void remover(Long id) {
		Cliente cliente = buscar(id);
		repository.delete(cliente);
		// flush força o DELETE a ir pro banco agora — se o cliente tiver operações
		// vinculadas, o erro (409, tratado globalmente) aparece já nesta chamada.
		repository.flush();
	}

}

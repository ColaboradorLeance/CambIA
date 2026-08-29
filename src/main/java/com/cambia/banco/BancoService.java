package com.cambia.banco;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class BancoService {

	private final BancoRepository repository;
	private final CalculoRepository calculoRepository;

	BancoService(BancoRepository repository, CalculoRepository calculoRepository) {
		this.repository = repository;
		this.calculoRepository = calculoRepository;
	}

	Banco criar(BancoRequest request) {
		Calculo calculo = buscarCalculo(request.calculoId());
		Banco banco = new Banco(request.codigoBanco(), request.sigla(), request.nome(), request.taxaRebate(), calculo);
		return repository.save(banco);
	}

	Banco buscar(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Banco não encontrado"));
	}

	List<Banco> listar() {
		return repository.findAll();
	}

	Banco atualizar(Long id, BancoRequest request) {
		Calculo calculo = buscarCalculo(request.calculoId());
		Banco banco = buscar(id);
		banco.atualizar(request.codigoBanco(), request.sigla(), request.nome(), request.taxaRebate(), calculo);
		return repository.save(banco);
	}

	void remover(Long id) {
		Banco banco = buscar(id);
		repository.delete(banco);
		// flush força o DELETE a ir pro banco agora — se o banco tiver operações
		// vinculadas, o erro (409, tratado globalmente) aparece já nesta chamada.
		repository.flush();
	}

	private Calculo buscarCalculo(Long calculoId) {
		return calculoRepository.findById(calculoId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cálculo não encontrado"));
	}

}

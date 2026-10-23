package com.cambia.banco;

import java.util.ArrayList;
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
		List<Calculo> calculos = buscarCalculos(request, List.of());
		Banco banco = new Banco(request.codigoBanco(), request.sigla(), request.nome(), request.taxaRebate(), calculos);
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
		Banco banco = buscar(id);
		List<Calculo> calculos = buscarCalculos(request, banco.getCalculos());
		// Tirar um cálculo do banco não mexe nas ordens que já o usam (decisão do PO,
		// Incremento 84): a ordem guarda o próprio cálculo.
		banco.atualizar(request.codigoBanco(), request.sigla(), request.nome(), request.taxaRebate(), calculos);
		return repository.save(banco);
	}

	void remover(Long id) {
		Banco banco = buscar(id);
		repository.delete(banco);
		// flush força o DELETE a ir pro banco agora — se o banco tiver operações
		// vinculadas, o erro (409, tratado globalmente) aparece já nesta chamada.
		repository.flush();
	}

	/**
	 * @param jaDoBanco cálculos que o banco já tem: um deles inativo pode continuar marcado
	 *                  (Incremento 85); marcar um inativo novo, não.
	 */
	private List<Calculo> buscarCalculos(BancoRequest request, List<Calculo> jaDoBanco) {
		List<Long> ids = request.calculosInformados();
		if (ids.isEmpty()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe ao menos um modelo de cálculo do banco");
		}
		List<Calculo> calculos = new ArrayList<>();
		for (Long calculoId : ids) {
			Calculo calculo = calculoRepository.findById(calculoId)
					.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cálculo não encontrado"));
			boolean jaTinha = jaDoBanco.stream().anyMatch(c -> c.getId().equals(calculoId));
			if (!calculo.isAtivo() && !jaTinha) {
				throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
						"O modelo de cálculo \"" + calculo.getNome() + "\" está inativo");
			}
			calculos.add(calculo);
		}
		return calculos;
	}

}

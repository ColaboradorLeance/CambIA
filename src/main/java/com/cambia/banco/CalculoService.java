package com.cambia.banco;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class CalculoService {

	private final CalculoRepository repository;
	private final BancoRepository bancoRepository;

	CalculoService(CalculoRepository repository, BancoRepository bancoRepository) {
		this.repository = repository;
		this.bancoRepository = bancoRepository;
	}

	Calculo criar(CalculoRequest request) {
		validarFormula(request.formula());
		Calculo calculo = new Calculo(request.nome(), request.formula());
		return repository.save(calculo);
	}

	Calculo buscar(Long id) {
		return repository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cálculo não encontrado"));
	}

	List<Calculo> listar() {
		return repository.findAll();
	}

	Calculo atualizar(Long id, CalculoRequest request) {
		validarFormula(request.formula());
		Calculo calculo = buscar(id);
		calculo.atualizar(request.nome(), request.formula());
		return repository.save(calculo);
	}

	void remover(Long id) {
		Calculo calculo = buscar(id);
		if (bancoRepository.existsByCalculoId(id)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Este cálculo está em uso por um ou mais bancos e não pode ser removido");
		}
		repository.delete(calculo);
	}

	private void validarFormula(String formula) {
		try {
			FormulaComissao.validar(formula);
		} catch (IllegalArgumentException | ArithmeticException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fórmula inválida: " + e.getMessage());
		}
	}

	TesteFormulaResponse testarFormula(TesteFormulaRequest request) {
		try {
			BigDecimal resultado = new FormulaComissao(request.formula())
					.avaliar(request.totalBrutoCambioExemplo(), request.taxaRebateExemplo())
					.setScale(2, RoundingMode.HALF_UP);
			return new TesteFormulaResponse(resultado);
		} catch (IllegalArgumentException | ArithmeticException e) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fórmula inválida: " + e.getMessage());
		}
	}

}

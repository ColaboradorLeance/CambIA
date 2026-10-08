package com.cambia.banco;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BancoRepository extends JpaRepository<Banco, Long> {

	boolean existsByCalculoId(Long calculoId);

	@Query("select b.nome from Banco b where b.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

	// left join: um banco sem modelo de cálculo (fórmula null) ainda precisa devolver
	// nome e taxa de rebate — mesmo contrato dos finders individuais que isto substitui
	// nas listagens de Operações.
	@Query("select new com.cambia.banco.BancoResumo(b.nome, c.formula, b.taxaRebate) from Banco b left join b.calculo c where b.id = :id")
	Optional<BancoResumo> findResumoById(@Param("id") Long id);

}

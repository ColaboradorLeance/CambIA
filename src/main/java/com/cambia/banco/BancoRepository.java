package com.cambia.banco;

import java.math.BigDecimal;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BancoRepository extends JpaRepository<Banco, Long> {

	@Query("select b.calculo.formula from Banco b where b.id = :id")
	Optional<String> findCalculoFormulaById(@Param("id") Long id);

	@Query("select b.taxaRebate from Banco b where b.id = :id")
	Optional<BigDecimal> findTaxaRebateById(@Param("id") Long id);

	boolean existsByCalculoId(Long calculoId);

	@Query("select b.nome from Banco b where b.id = :id")
	Optional<String> findNomeById(@Param("id") Long id);

}

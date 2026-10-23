package com.cambia.banco;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CalculoRepository extends JpaRepository<Calculo, Long> {

	@Query("select new com.cambia.banco.CalculoResumo(c.nome, c.formula) from Calculo c where c.id = :id")
	Optional<CalculoResumo> findResumoById(@Param("id") Long id);

	// Nativa: Operacao fica em outro pacote e o relacionamento não é mapeado deste lado.
	@Query(value = "select exists(select 1 from operacoes where modelo_calculo_id = :id)", nativeQuery = true)
	boolean usadoEmOrdens(@Param("id") Long id);

}

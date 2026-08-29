package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface FechamentoGeradoRepository extends JpaRepository<FechamentoGerado, Long> {

	boolean existsByData(LocalDate data);

	List<FechamentoGerado> findAllByOrderByGeradoEmDesc();

}

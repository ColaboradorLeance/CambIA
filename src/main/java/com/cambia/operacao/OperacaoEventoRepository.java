package com.cambia.operacao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface OperacaoEventoRepository extends JpaRepository<OperacaoEvento, Long> {

	List<OperacaoEvento> findAllByOrderByCriadoEmDescIdDesc();

}

package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface OperacaoRepository extends JpaRepository<Operacao, Long> {

	long countByIdTradeStartingWith(String prefixo);

	List<Operacao> findByData(LocalDate data);

	List<Operacao> findByStatus(StatusOperacao status);

	@Query("""
			SELECT o FROM Operacao o
			WHERE o.data BETWEEN :inicio AND :fim
			AND (:clienteId IS NULL OR o.clienteId = :clienteId)
			AND (:bancoId IS NULL OR o.bancoId = :bancoId)
			AND (:moeda IS NULL OR o.moeda = :moeda)
			AND (:cv IS NULL OR o.cv = :cv)
			AND (:status IS NULL OR o.status = :status)
			AND (:criadoPorUsuarioId IS NULL OR o.criadoPorUsuarioId = :criadoPorUsuarioId)
			AND (:completadoPorUsuarioId IS NULL OR o.completadoPorUsuarioId = :completadoPorUsuarioId)
			ORDER BY o.data DESC, o.id DESC
			""")
	List<Operacao> buscarFiltrado(LocalDate inicio, LocalDate fim, Long clienteId, Long bancoId, String moeda,
			String cv, StatusOperacao status, Long criadoPorUsuarioId, Long completadoPorUsuarioId);

}

package com.cambia.operacao;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface OperacaoRepository extends JpaRepository<Operacao, Long>, JpaSpecificationExecutor<Operacao> {

	// Incremento 80: opções dos <select> de Moeda e Fundo da listagem paginada. Antes vinham
	// das ordens carregadas no navegador — com paginação, a página atual não tem todas.
	@Query("select distinct o.moeda from Operacao o where o.status = :status order by o.moeda")
	List<String> moedasDistintas(@Param("status") StatusOperacao status);

	@Query("select distinct o.fundo from Operacao o where o.status = :status order by o.fundo")
	List<String> fundosDistintos(@Param("status") StatusOperacao status);

	// Incremento 78: incremento atômico do contador do ano no próprio PostgreSQL — o
	// upsert trava só a linha daquele ano pelo instante do incremento, então gravações
	// simultâneas nunca recebem o mesmo número (o antigo "count + 1" recebia).
	// Roda fora de transação própria (autocommit), por isso uma gravação que falhar
	// DEPOIS de pegar o número deixa um buraco na numeração — mesmo comportamento de
	// uma sequence do banco, e o preço aceitável por nunca repetir código.
	@Query(value = """
			INSERT INTO id_trade_contadores (ano, ultimo) VALUES (:ano, 1)
			ON CONFLICT (ano) DO UPDATE SET ultimo = id_trade_contadores.ultimo + 1
			RETURNING ultimo""", nativeQuery = true)
	long proximoSequencialIdTrade(@Param("ano") int ano);

	List<Operacao> findByData(LocalDate data);

	// Ordem EXPLÍCITA (de criação): sem ORDER BY, a ordem das linhas é a que o banco
	// escolher — e mudou quando os índices do Incremento 80 entraram, embaralhando a
	// exposição por cliente/banco da Posição em aberto e do Fechamento.
	List<Operacao> findByStatusOrderByIdAsc(StatusOperacao status);

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

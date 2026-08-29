package com.cambia.operacao;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operacoes_eventos")
class OperacaoEvento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "operacao_id")
	private Long operacaoId;

	@Enumerated(EnumType.STRING)
	private TipoEventoOperacao tipo;

	@Column(name = "usuario_id")
	private Long usuarioId;

	@Column(name = "criado_em")
	private Instant criadoEm;

	@Column(name = "dados_anteriores", columnDefinition = "TEXT")
	private String dadosAnteriores;

	protected OperacaoEvento() {
	}

	OperacaoEvento(Long operacaoId, TipoEventoOperacao tipo, Long usuarioId, Instant criadoEm,
			String dadosAnteriores) {
		this.operacaoId = operacaoId;
		this.tipo = tipo;
		this.usuarioId = usuarioId;
		this.criadoEm = criadoEm;
		this.dadosAnteriores = dadosAnteriores;
	}

	Long getId() {
		return id;
	}

	Long getOperacaoId() {
		return operacaoId;
	}

	TipoEventoOperacao getTipo() {
		return tipo;
	}

	Long getUsuarioId() {
		return usuarioId;
	}

	Instant getCriadoEm() {
		return criadoEm;
	}

	String getDadosAnteriores() {
		return dadosAnteriores;
	}

}

package com.cambia.operacao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "operacoes")
class Operacao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "id_trade")
	private String idTrade;

	private LocalDate data;

	@Column(name = "codigo_banco")
	private String codigoBanco;

	@Column(name = "cliente_id")
	private Long clienteId;

	@Column(name = "banco_id")
	private Long bancoId;

	private String cv;

	@Column(name = "pr_cr_vir")
	private String prCrVir;

	private String moeda;

	@Column(name = "valor_me")
	private BigDecimal valorMe;

	@Column(name = "spot_asset")
	private BigDecimal spotAsset;

	private BigDecimal nivelamento;

	@Column(name = "taxa_final")
	private BigDecimal taxaFinal;

	@Enumerated(EnumType.STRING)
	private StatusOperacao status;

	@Column(name = "criado_por_usuario_id")
	private Long criadoPorUsuarioId;

	@Column(name = "criado_em")
	private Instant criadoEm;

	@Column(name = "completado_por_usuario_id")
	private Long completadoPorUsuarioId;

	@Column(name = "completado_em")
	private Instant completadoEm;

	protected Operacao() {
	}

	Operacao(String idTrade, LocalDate data, String codigoBanco, Long clienteId, Long bancoId, String cv,
			String prCrVir, String moeda, BigDecimal valorMe, BigDecimal spotAsset, BigDecimal nivelamento,
			BigDecimal taxaFinal, Long criadoPorUsuarioId, Instant criadoEm) {
		this.idTrade = idTrade;
		this.data = data;
		this.codigoBanco = codigoBanco;
		this.clienteId = clienteId;
		this.bancoId = bancoId;
		this.cv = cv;
		this.prCrVir = prCrVir;
		this.moeda = moeda;
		this.valorMe = valorMe;
		this.spotAsset = spotAsset;
		this.nivelamento = nivelamento;
		this.taxaFinal = taxaFinal;
		this.status = StatusOperacao.ANDAMENTO;
		this.criadoPorUsuarioId = criadoPorUsuarioId;
		this.criadoEm = criadoEm;
	}

	Long getId() {
		return id;
	}

	String getIdTrade() {
		return idTrade;
	}

	LocalDate getData() {
		return data;
	}

	String getCodigoBanco() {
		return codigoBanco;
	}

	Long getClienteId() {
		return clienteId;
	}

	Long getBancoId() {
		return bancoId;
	}

	String getCv() {
		return cv;
	}

	String getPrCrVir() {
		return prCrVir;
	}

	String getMoeda() {
		return moeda;
	}

	BigDecimal getValorMe() {
		return valorMe;
	}

	BigDecimal getSpotAsset() {
		return spotAsset;
	}

	BigDecimal getNivelamento() {
		return nivelamento;
	}

	BigDecimal getTaxaFinal() {
		return taxaFinal;
	}

	StatusOperacao getStatus() {
		return status;
	}

	Long getCriadoPorUsuarioId() {
		return criadoPorUsuarioId;
	}

	Instant getCriadoEm() {
		return criadoEm;
	}

	Long getCompletadoPorUsuarioId() {
		return completadoPorUsuarioId;
	}

	Instant getCompletadoEm() {
		return completadoEm;
	}

	void editar(String codigoBanco, Long clienteId, Long bancoId, String cv, String prCrVir,
			String moeda, BigDecimal valorMe, BigDecimal spotAsset, BigDecimal nivelamento, BigDecimal taxaFinal) {
		// Data não entra aqui de propósito: não pode ser editada depois de criada (pedido do usuário).
		this.codigoBanco = codigoBanco;
		this.clienteId = clienteId;
		this.bancoId = bancoId;
		this.cv = cv;
		this.prCrVir = prCrVir;
		this.moeda = moeda;
		this.valorMe = valorMe;
		this.spotAsset = spotAsset;
		this.nivelamento = nivelamento;
		this.taxaFinal = taxaFinal;
	}

	void atualizarStatus(StatusOperacao novoStatus, Long usuarioId, Instant agora) {
		this.status = novoStatus;
		if (novoStatus == StatusOperacao.CONFIRMADO) {
			this.completadoPorUsuarioId = usuarioId;
			this.completadoEm = agora;
		}
	}

}

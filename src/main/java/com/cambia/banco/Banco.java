package com.cambia.banco;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "bancos")
class Banco {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "codigo_banco")
	private String codigoBanco;

	private String sigla;

	private String nome;

	@Column(name = "taxa_rebate")
	private BigDecimal taxaRebate;

	@ManyToOne
	@JoinColumn(name = "modelo_calculo_id")
	private Calculo calculo;

	protected Banco() {
	}

	Banco(String codigoBanco, String sigla, String nome, BigDecimal taxaRebate, Calculo calculo) {
		this.codigoBanco = codigoBanco;
		this.sigla = sigla;
		this.nome = nome;
		this.taxaRebate = taxaRebate;
		this.calculo = calculo;
	}

	Long getId() {
		return id;
	}

	String getCodigoBanco() {
		return codigoBanco;
	}

	String getSigla() {
		return sigla;
	}

	String getNome() {
		return nome;
	}

	BigDecimal getTaxaRebate() {
		return taxaRebate;
	}

	Calculo getCalculo() {
		return calculo;
	}

	void atualizar(String codigoBanco, String sigla, String nome, BigDecimal taxaRebate, Calculo calculo) {
		this.codigoBanco = codigoBanco;
		this.sigla = sigla;
		this.nome = nome;
		this.taxaRebate = taxaRebate;
		this.calculo = calculo;
	}

}

package com.cambia.banco;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
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

	// Incremento 84: um ou mais modelos de cálculo; a ordem escolhe qual usar quando há
	// mais de um. EAGER: bancos são poucos e toda tela de banco/ordem precisa da lista.
	@ManyToMany(fetch = FetchType.EAGER)
	@JoinTable(name = "banco_calculos", joinColumns = @JoinColumn(name = "banco_id"),
			inverseJoinColumns = @JoinColumn(name = "modelo_calculo_id"))
	private Set<Calculo> calculos = new HashSet<>();

	protected Banco() {
	}

	Banco(String codigoBanco, String sigla, String nome, BigDecimal taxaRebate, Collection<Calculo> calculos) {
		this.codigoBanco = codigoBanco;
		this.sigla = sigla;
		this.nome = nome;
		this.taxaRebate = taxaRebate;
		this.calculos.addAll(calculos);
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

	/** Em ordem de nome — a ordem que a tela mostra e o select da ordem oferece. */
	List<Calculo> getCalculos() {
		return calculos.stream().sorted(Comparator.comparing(Calculo::getNome).thenComparing(Calculo::getId)).toList();
	}

	void atualizar(String codigoBanco, String sigla, String nome, BigDecimal taxaRebate, Collection<Calculo> calculos) {
		this.codigoBanco = codigoBanco;
		this.sigla = sigla;
		this.nome = nome;
		this.taxaRebate = taxaRebate;
		this.calculos.clear();
		this.calculos.addAll(calculos);
	}

}

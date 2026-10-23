package com.cambia.banco;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "modelos_calculo")
class Calculo {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nome;

	private String formula;

	// Incremento 85: inativo continua valendo onde já está (bancos e ordens), mas ordens
	// novas não podem escolhê-lo.
	private boolean ativo = true;

	protected Calculo() {
	}

	Calculo(String nome, String formula) {
		this.nome = nome;
		this.formula = formula;
	}

	Long getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	String getFormula() {
		return formula;
	}

	boolean isAtivo() {
		return ativo;
	}

	void definirAtivo(boolean ativo) {
		this.ativo = ativo;
	}

	void atualizar(String nome, String formula) {
		this.nome = nome;
		this.formula = formula;
	}

}

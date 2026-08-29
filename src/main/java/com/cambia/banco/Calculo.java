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

	void atualizar(String nome, String formula) {
		this.nome = nome;
		this.formula = formula;
	}

}

package com.cambia.cliente;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "clientes")
class Cliente {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nome;
	private String documento;

	protected Cliente() {
	}

	Cliente(String nome, String documento) {
		this.nome = nome;
		this.documento = documento;
	}

	Long getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	String getDocumento() {
		return documento;
	}

	void atualizar(String nome, String documento) {
		this.nome = nome;
		this.documento = documento;
	}

}

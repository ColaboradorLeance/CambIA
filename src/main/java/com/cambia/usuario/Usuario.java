package com.cambia.usuario;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nome;
	private String email;

	@Enumerated(EnumType.STRING)
	private Perfil perfil;

	protected Usuario() {
	}

	public Usuario(String nome, String email, Perfil perfil) {
		this.nome = nome;
		this.email = email;
		this.perfil = perfil;
	}

	public Long getId() {
		return id;
	}

	public String getNome() {
		return nome;
	}

	public String getEmail() {
		return email;
	}

	public Perfil getPerfil() {
		return perfil;
	}

	void atualizar(String nome, String email, Perfil perfil) {
		this.nome = nome;
		this.email = email;
		this.perfil = perfil;
	}

}

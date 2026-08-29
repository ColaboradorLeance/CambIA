package com.cambia.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "sessoes")
public class Sessao {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long usuarioId;
	private String token;
	private Instant expiraEm;

	protected Sessao() {
	}

	public Sessao(Long usuarioId, String token, Instant expiraEm) {
		this.usuarioId = usuarioId;
		this.token = token;
		this.expiraEm = expiraEm;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getToken() {
		return token;
	}

	public boolean isValida(Instant agora) {
		return agora.isBefore(expiraEm);
	}

}

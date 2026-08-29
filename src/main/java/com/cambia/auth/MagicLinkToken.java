package com.cambia.auth;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "magic_link_tokens")
public class MagicLinkToken {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private Long usuarioId;
	private String token;
	private Instant expiraEm;
	private boolean usado;

	protected MagicLinkToken() {
	}

	public MagicLinkToken(Long usuarioId, String token, Instant expiraEm) {
		this.usuarioId = usuarioId;
		this.token = token;
		this.expiraEm = expiraEm;
		this.usado = false;
	}

	public Long getUsuarioId() {
		return usuarioId;
	}

	public String getToken() {
		return token;
	}

	boolean isValido(Instant agora) {
		return !usado && agora.isBefore(expiraEm);
	}

	void marcarUsado() {
		this.usado = true;
	}

}

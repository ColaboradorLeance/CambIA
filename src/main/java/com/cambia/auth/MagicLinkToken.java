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
	private String vinculo;

	protected MagicLinkToken() {
	}

	public MagicLinkToken(Long usuarioId, String token, Instant expiraEm) {
		this(usuarioId, token, expiraEm, null);
	}

	// Achado de revisão de segurança ("login CSRF" em /auth/verify): vinculo é o nonce
	// gravado num cookie httpOnly na hora em que o link foi pedido (ver AuthController) —
	// null quando a requisição de /auth/magic-link não veio por HTTPS (dev local, onde o
	// cookie não seria aceito de qualquer forma), caso em que nenhum vínculo é exigido na
	// hora de verificar, mantendo o comportamento de antes desta correção.
	public MagicLinkToken(Long usuarioId, String token, Instant expiraEm, String vinculo) {
		this.usuarioId = usuarioId;
		this.token = token;
		this.expiraEm = expiraEm;
		this.usado = false;
		this.vinculo = vinculo;
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

	// vinculo nulo == nenhum vínculo foi exigido na hora do pedido (dev local sem HTTPS) —
	// compatível com qualquer requisição de verify, vinculado ou não. Quando não é nulo, só
	// é compatível com o mesmo valor exato (o cookie httpOnly gravado na hora do pedido).
	boolean vinculoCompativel(String vinculoRecebido) {
		return vinculo == null || vinculo.equals(vinculoRecebido);
	}

	void marcarUsado() {
		this.usado = true;
	}

}

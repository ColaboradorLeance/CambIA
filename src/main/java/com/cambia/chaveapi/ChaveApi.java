package com.cambia.chaveapi;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "chaves_api")
class ChaveApi {

	// Gravar ultimo_uso_em a cada requisição transformaria toda leitura de uma integração
	// numa escrita no banco; com uma janela, a informação continua útil pra detectar chave
	// esquecida ou usada fora do esperado, a um custo de no máximo 1 UPDATE a cada 5 min.
	private static final Duration JANELA_ULTIMO_USO = Duration.ofMinutes(5);

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String nome;

	@Column(name = "usuario_id")
	private Long usuarioId;

	private String prefixo;
	private String hash;

	@Column(name = "criada_em")
	private Instant criadaEm;

	@Column(name = "criada_por_usuario_id")
	private Long criadaPorUsuarioId;

	@Column(name = "expira_em")
	private LocalDate expiraEm;

	@Column(name = "ultimo_uso_em")
	private Instant ultimoUsoEm;

	@Column(name = "revogada_em")
	private Instant revogadaEm;

	@Column(name = "revogada_por_usuario_id")
	private Long revogadaPorUsuarioId;

	protected ChaveApi() {
	}

	ChaveApi(String nome, Long usuarioId, String prefixo, String hash, Instant criadaEm, Long criadaPorUsuarioId,
			LocalDate expiraEm) {
		this.nome = nome;
		this.usuarioId = usuarioId;
		this.prefixo = prefixo;
		this.hash = hash;
		this.criadaEm = criadaEm;
		this.criadaPorUsuarioId = criadaPorUsuarioId;
		this.expiraEm = expiraEm;
	}

	/** Válida até o fim do dia de expiração (inclusive); revogada nunca volta a valer (D5). */
	boolean isAtiva(LocalDate hoje) {
		return revogadaEm == null && (expiraEm == null || !hoje.isAfter(expiraEm));
	}

	boolean isRevogada() {
		return revogadaEm != null;
	}

	void revogar(Long usuarioId, Instant agora) {
		this.revogadaEm = agora;
		this.revogadaPorUsuarioId = usuarioId;
	}

	/** @return {@code true} se o último uso mudou e a entidade precisa ser salva. */
	boolean registrarUso(Instant agora) {
		if (ultimoUsoEm != null && ultimoUsoEm.isAfter(agora.minus(JANELA_ULTIMO_USO))) {
			return false;
		}
		ultimoUsoEm = agora;
		return true;
	}

	Long getId() {
		return id;
	}

	String getNome() {
		return nome;
	}

	Long getUsuarioId() {
		return usuarioId;
	}

	String getPrefixo() {
		return prefixo;
	}

	String getHash() {
		return hash;
	}

	Instant getCriadaEm() {
		return criadaEm;
	}

	Long getCriadaPorUsuarioId() {
		return criadaPorUsuarioId;
	}

	LocalDate getExpiraEm() {
		return expiraEm;
	}

	Instant getUltimoUsoEm() {
		return ultimoUsoEm;
	}

	Instant getRevogadaEm() {
		return revogadaEm;
	}

	Long getRevogadaPorUsuarioId() {
		return revogadaPorUsuarioId;
	}

}

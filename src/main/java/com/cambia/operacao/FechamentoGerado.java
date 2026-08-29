package com.cambia.operacao;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "fechamentos_gerados")
class FechamentoGerado {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private LocalDate data;

	private String formato;

	@Column(name = "nome_arquivo")
	private String nomeArquivo;

	private byte[] conteudo;

	@Column(name = "gerado_em")
	private Instant geradoEm;

	protected FechamentoGerado() {
	}

	FechamentoGerado(LocalDate data, String formato, String nomeArquivo, byte[] conteudo, Instant geradoEm) {
		this.data = data;
		this.formato = formato;
		this.nomeArquivo = nomeArquivo;
		this.conteudo = conteudo;
		this.geradoEm = geradoEm;
	}

	Long getId() {
		return id;
	}

	LocalDate getData() {
		return data;
	}

	String getFormato() {
		return formato;
	}

	String getNomeArquivo() {
		return nomeArquivo;
	}

	byte[] getConteudo() {
		return conteudo;
	}

	Instant getGeradoEm() {
		return geradoEm;
	}

}

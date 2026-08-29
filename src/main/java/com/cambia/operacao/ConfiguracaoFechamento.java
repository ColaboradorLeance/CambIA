package com.cambia.operacao;

import java.time.LocalTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "configuracao_fechamento")
class ConfiguracaoFechamento {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "hora_execucao")
	private LocalTime horaExecucao;

	protected ConfiguracaoFechamento() {
	}

	ConfiguracaoFechamento(LocalTime horaExecucao) {
		this.horaExecucao = horaExecucao;
	}

	Long getId() {
		return id;
	}

	LocalTime getHoraExecucao() {
		return horaExecucao;
	}

	void atualizar(LocalTime horaExecucao) {
		this.horaExecucao = horaExecucao;
	}

}

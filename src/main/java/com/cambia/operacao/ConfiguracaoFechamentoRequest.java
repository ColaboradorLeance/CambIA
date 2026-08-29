package com.cambia.operacao;

import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;

record ConfiguracaoFechamentoRequest(@NotNull LocalTime horaExecucao) {
}

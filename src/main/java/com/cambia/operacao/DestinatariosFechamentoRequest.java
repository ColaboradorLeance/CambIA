package com.cambia.operacao;

import java.util.List;

import jakarta.validation.constraints.NotNull;

record DestinatariosFechamentoRequest(@NotNull List<Long> usuarioIds) {
}

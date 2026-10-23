package com.cambia.banco;

import jakarta.validation.constraints.NotNull;

/** Corpo do PATCH /calculos/{id}/ativo (Incremento 85). */
record AtivoRequest(@NotNull Boolean ativo) {
}

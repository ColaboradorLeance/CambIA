package com.cambia.operacao;

import jakarta.validation.constraints.NotNull;

record StatusUpdateRequest(@NotNull StatusOperacao status) {
}

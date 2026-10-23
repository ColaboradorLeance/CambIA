package com.cambia.chaveapi;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Criação de chave pelo próprio dono (Incremento 81). Escopos chegam como texto para o erro
 * de escopo inválido sair com mensagem clara, e não como falha de leitura do JSON.
 */
record PerfilChaveApiRequest(
		@NotBlank @Size(max = 120) String nome,
		List<String> escopos,
		// K5: ausente = hoje + 1 ano; nunca depois de hoje + 1 ano.
		LocalDate expiraEm) {
}

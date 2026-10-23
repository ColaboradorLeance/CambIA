package com.cambia.chaveapi;

import java.util.Optional;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Qual chave autenticou a requisição atual (Incremento 81, K9). O filtro de autenticação
 * guarda isto nos detalhes da autenticação; quem grava auditoria (o evento da ordem)
 * consulta por aqui, sem depender do pacote de segurança. Vazio = sessão de tela.
 */
public record ChaveApiNaRequisicao(Long id, String nome) {

	public static Optional<ChaveApiNaRequisicao> atual() {
		Authentication autenticacao = SecurityContextHolder.getContext().getAuthentication();
		if (autenticacao != null && autenticacao.getDetails() instanceof ChaveApiNaRequisicao chave) {
			return Optional.of(chave);
		}
		return Optional.empty();
	}

	public static Long idAtual() {
		return atual().map(ChaveApiNaRequisicao::id).orElse(null);
	}

}

package com.cambia.cliente;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Consulta a BrasilAPI (https://brasilapi.com.br/api/cnpj/v1/{cnpj}) — fonte pública,
 * gratuita, sem chave de acesso, que agrega dados abertos da Receita Federal.
 * Qualquer falha (CNPJ não encontrado, fora do ar, timeout) é tratada como "sem
 * resultado" (Optional vazio), nunca propagada como erro — ver {@link ConsultaCnpjClient}.
 */
@Component
class BrasilApiConsultaCnpjClient implements ConsultaCnpjClient {

	private static final String BASE_URL = "https://brasilapi.com.br/api/cnpj/v1";

	private final RestClient restClient;

	BrasilApiConsultaCnpjClient() {
		this(RestClient.builder());
	}

	BrasilApiConsultaCnpjClient(RestClient.Builder builder) {
		this.restClient = builder.baseUrl(BASE_URL).build();
	}

	@Override
	public Optional<String> buscarNome(String cnpjSomenteDigitos) {
		try {
			Resposta resposta = restClient.get()
					.uri("/{cnpj}", cnpjSomenteDigitos)
					.retrieve()
					.body(Resposta.class);
			if (resposta == null) {
				return Optional.empty();
			}
			String nome = temTexto(resposta.razaoSocial()) ? resposta.razaoSocial() : resposta.nomeFantasia();
			return temTexto(nome) ? Optional.of(nome) : Optional.empty();
		} catch (RestClientException e) {
			return Optional.empty();
		}
	}

	private static boolean temTexto(String valor) {
		return valor != null && !valor.isBlank();
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	private record Resposta(
			@JsonProperty("razao_social") String razaoSocial,
			@JsonProperty("nome_fantasia") String nomeFantasia) {
	}

}

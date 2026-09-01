package com.cambia.security;

import java.io.IOException;
import java.util.List;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebSecurity
class SecurityConfig {

	// Bug encontrado ao vivo (não uma pendência de segurança, um erro de configuração):
	// esta lista só tinha a origem de dev local (Vite, sem o reverse-proxy). Atrás do
	// reverse-proxy (Fase 3A), front-end e backend viram a mesma origem — mas um
	// navegador de verdade ainda manda o header Origin em requisições POST/PUT/DELETE
	// mesmo sendo a MESMA origem (o curl usado pra verificar cada correção desta revisão
	// não manda esse header por padrão, por isso o problema não apareceu até um
	// navegador real tentar logar). Sem essa origem na lista, o próprio CorsFilter do
	// Spring rejeitava a requisição com 403 antes dela chegar em qualquer controller —
	// nem chegava a ser uma questão de CSRF. Valor configurável (deriva do mesmo
	// CERT_CN usado pro certificado — ver docker-compose.yml) porque a origem real de
	// produção depende de como o reverse-proxy foi acessado, não é sempre "localhost".
	@Value("${cambia.cors.origem-adicional:https://localhost}")
	private String origemCorsAdicional;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http, SessaoAuthenticationFilter sessaoAuthenticationFilter,
			CsrfProtectionFilter csrfProtectionFilter) throws Exception {
		http
				// CSRF do Spring Security continua desligado — a partir da Fase 3B a proteção é
				// feita por um filtro próprio (CsrfProtectionFilter, abaixo), só quando a
				// requisição depende do cookie de sessão (o header Authorization já é imune por
				// natureza). Ver a classe pra mais detalhes.
				.csrf(csrf -> csrf.disable())
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(auth -> auth
						.requestMatchers("/auth/**", "/actuator/health", "/error").permitAll()
						// Operações: Consultor só consulta; Admin e Analista criam, editam e completam
						.requestMatchers(HttpMethod.GET, "/operacoes/**").authenticated()
						.requestMatchers(HttpMethod.POST, "/operacoes").hasAnyRole("ADMIN", "ANALISTA")
						.requestMatchers(HttpMethod.PUT, "/operacoes/*").hasAnyRole("ADMIN", "ANALISTA")
						.requestMatchers(HttpMethod.PATCH, "/operacoes/*/status").hasAnyRole("ADMIN", "ANALISTA")
						// Configuração do fechamento automático (horário e destinatários): só Admin
						.requestMatchers(HttpMethod.PUT, "/fechamentos/configuracao", "/fechamentos/destinatarios")
								.hasRole("ADMIN")
						// Relatórios e Fechamento: Admin e Analista (Consultor não acessa)
						.requestMatchers("/relatorios/**", "/fechamentos/**").hasAnyRole("ADMIN", "ANALISTA")
						// Cadastros (Cliente/Banco/Modelo de Cálculo): Admin e Analista têm acesso completo
						.requestMatchers("/clientes/**", "/bancos/**", "/calculos/**").hasAnyRole("ADMIN", "ANALISTA")
						// Usuários: só Admin
						.requestMatchers("/usuarios/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.exceptionHandling(handling -> handling
						.authenticationEntryPoint((request, response, authException) -> escreverErro(response,
								HttpServletResponse.SC_UNAUTHORIZED, "Sessão expirada ou inválida. Faça login novamente."))
						.accessDeniedHandler((request, response, accessDeniedException) -> escreverErro(response,
								HttpServletResponse.SC_FORBIDDEN, "Seu perfil não tem permissão para esta ação.")))
				.addFilterBefore(csrfProtectionFilter, UsernamePasswordAuthenticationFilter.class)
				.addFilterBefore(sessaoAuthenticationFilter, CsrfProtectionFilter.class);
		return http.build();
	}

	// Corpo de erro (não vazio) pra 401/403, no mesmo formato "detail" usado pelos outros
	// erros da API (ProblemDetail) — sem isso o front-end via só um status sem explicação.
	private static void escreverErro(HttpServletResponse response, int status, String mensagem) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.getWriter().write("{\"detail\":\"" + mensagem + "\"}");
	}

	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuracao = new CorsConfiguration();
		configuracao.setAllowedOrigins(List.of("http://localhost:5173", origemCorsAdicional));
		configuracao.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
		configuracao.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
		// Fase 3B: precisa pra cookies (sessão/CSRF) trafegarem em requisição cross-origin —
		// só importa pra quem roda em dev sem o reverse-proxy; em produção (mesma origem,
		// atrás do proxy) o navegador já manda cookie independente disso.
		configuracao.setAllowCredentials(true);

		UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
		fonte.registerCorsConfiguration("/**", configuracao);
		return fonte;
	}

}

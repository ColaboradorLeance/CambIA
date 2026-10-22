package com.cambia.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.cambia.auth.LimitadorDeRequisicoes;
import com.cambia.auth.ResolvedorDeIpReal;
import com.cambia.chaveapi.ChaveApiService;
import com.cambia.usuario.Usuario;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.UrlPathHelper;

/**
 * Autenticação por chave de API (Incremento 79, decisões em docs/decisoes.md): header
 * {@code X-API-Key}, usado por integrações (sistema chamando sistema) em vez da sessão de
 * 8h do link mágico. Roda antes do {@link SessaoAuthenticationFilter}.
 *
 * <p>Sem o header, não faz nada — nem consulta o banco —, então o fluxo de sessão das
 * telas fica intocado. Com o header, a decisão é definitiva aqui: chave inválida responde
 * na hora (não cai pra sessão nem pra cookie), e chave válida autentica como o usuário
 * vinculado, com o perfil dele (D2/D6) mais a marca {@link #AUTORIDADE}.
 */
@Component
class ChaveApiAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";
	static final String AUTORIDADE = "CHAVE_API";

	// D7: chave nunca administra usuários nem chaves, nem abre/fecha sessão — vale mesmo
	// que o perfil do usuário vinculado permitisse. Sem isso, uma chave vazada poderia
	// criar outras chaves e sobreviver à própria revogação.
	private static final List<String> CAMINHOS_PROIBIDOS = List.of("/usuarios", "/chaves-api", "/auth");

	private final ChaveApiService chaveApiService;
	private final LimitadorDeRequisicoes limitador;

	ChaveApiAuthenticationFilter(ChaveApiService chaveApiService, LimitadorDeRequisicoes limitador) {
		this.chaveApiService = chaveApiService;
		this.limitador = limitador;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String chave = request.getHeader(HEADER);
		if (chave == null) {
			chain.doFilter(request, response);
			return;
		}

		Optional<Usuario> usuario = chaveApiService.autenticar(chave.trim());
		if (usuario.isEmpty()) {
			// Força bruta num segredo de 256 bits é inviável; o limite por IP é defesa em
			// profundidade (responde 429 a quem insiste). Só tentativas INVÁLIDAS contam —
			// integração legítima nunca esbarra nele —, por isso a checagem vem depois do
			// lookup: ela não poupa a consulta ao banco de quem já foi bloqueado.
			if (!limitador.permitir("chave-api:" + ResolvedorDeIpReal.resolver(request))) {
				escreverErro(response, 429, "Muitas tentativas com chave de API inválida. Aguarde alguns minutos.");
				return;
			}
			// Mesma resposta pra inexistente, revogada e expirada: não revelar qual chave
			// já existiu. Quem administra vê o motivo na tela de Chaves de API.
			escreverErro(response, HttpServletResponse.SC_UNAUTHORIZED, "Chave de API inválida, revogada ou expirada.");
			return;
		}

		if (caminhoProibido(request)) {
			escreverErro(response, HttpServletResponse.SC_FORBIDDEN,
					"Chave de API não pode gerenciar usuários, chaves de API ou sessões.");
			return;
		}

		Usuario autenticado = usuario.get();
		List<GrantedAuthority> autoridades = List.of(
				new SimpleGrantedAuthority("ROLE_" + autenticado.getPerfil().name()),
				new SimpleGrantedAuthority(AUTORIDADE));
		SecurityContextHolder.getContext()
				.setAuthentication(new UsernamePasswordAuthenticationToken(autenticado, null, autoridades));
		chain.doFilter(request, response);
	}

	/** Usado por SessaoAuthenticationFilter e CsrfProtectionFilter pra saber que a chave já decidiu. */
	static boolean autenticadoPorChave() {
		var autenticacao = SecurityContextHolder.getContext().getAuthentication();
		return autenticacao != null && autenticacao.getAuthorities().stream()
				.anyMatch(autoridade -> AUTORIDADE.equals(autoridade.getAuthority()));
	}

	private boolean caminhoProibido(HttpServletRequest request) {
		// Caminho DECODIFICADO (o mesmo que o Spring MVC usa pra rotear): com o
		// getRequestURI() cru, "/%75suarios" ou "/chaves%2Dapi" escapavam desta lista e
		// ainda assim chegavam no controller.
		String caminho = UrlPathHelper.defaultInstance.getPathWithinApplication(request);
		return CAMINHOS_PROIBIDOS.stream()
				.anyMatch(proibido -> caminho.equals(proibido) || caminho.startsWith(proibido + "/"));
	}

	// Mesmo formato {"detail": ...} do SecurityConfig e do CsrfProtectionFilter; as
	// mensagens são constantes (nunca ecoam o header recebido), então não há o que escapar.
	private static void escreverErro(HttpServletResponse response, int status, String mensagem) throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding("UTF-8");
		response.setContentType("application/json");
		response.getWriter().write("{\"detail\":\"" + mensagem + "\"}");
	}

}

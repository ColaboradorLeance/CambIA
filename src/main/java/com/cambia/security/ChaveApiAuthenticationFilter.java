package com.cambia.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import com.cambia.auth.ResolvedorDeIpReal;
import com.cambia.chaveapi.ChaveApiNaRequisicao;
import com.cambia.chaveapi.ChaveApiService;
import com.cambia.chaveapi.ChaveAutenticada;
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
 * na hora (não cai pra sessão nem pra cookie), e chave válida autentica como o dono, com o
 * perfil dele mais a marca {@link #AUTORIDADE}.
 *
 * <p>Incremento 81: depois da D7, confere o ESCOPO da chave (K4) pelo método e pelo caminho
 * decodificado; fora do escopo responde 403 aqui mesmo. O perfil do dono continua sendo
 * conferido depois, no SecurityConfig (K6) — uma chave nunca faz mais do que o dono.
 */
@Component
class ChaveApiAuthenticationFilter extends OncePerRequestFilter {

	static final String HEADER = "X-API-Key";
	static final String AUTORIDADE = "CHAVE_API";

	// D7: chave nunca administra usuários nem chaves, nem abre/fecha sessão — vale mesmo
	// que o perfil do dono permitisse. Sem isso, uma chave vazada poderia criar outras
	// chaves e sobreviver à própria revogação. Incremento 81: inclui /perfil, onde o
	// usuário gerencia as próprias chaves.
	private static final List<String> CAMINHOS_PROIBIDOS = List.of("/usuarios", "/chaves-api", "/perfil", "/auth");

	private final ChaveApiService chaveApiService;
	private final LimitadorTentativasChaveApi limitador;

	ChaveApiAuthenticationFilter(ChaveApiService chaveApiService, LimitadorTentativasChaveApi limitador) {
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

		Optional<ChaveAutenticada> autenticada = chaveApiService.autenticar(chave.trim());
		if (autenticada.isEmpty()) {
			// Força bruta num segredo de 256 bits é inviável; o limite por IP é defesa em
			// profundidade (responde 429 a quem insiste). Só tentativas INVÁLIDAS contam —
			// integração legítima nunca esbarra nele —, por isso a checagem vem depois do
			// lookup: ela não poupa a consulta ao banco de quem já foi bloqueado.
			if (!limitador.permitir(ResolvedorDeIpReal.resolver(request))) {
				escreverErro(response, 429, "Muitas tentativas com chave de API inválida. Aguarde alguns minutos.");
				return;
			}
			// Mesma resposta pra inexistente, revogada e expirada: não revelar qual chave
			// já existiu. Quem administra vê o motivo na tela de Chaves de API.
			escreverErro(response, HttpServletResponse.SC_UNAUTHORIZED, "Chave de API inválida, revogada ou expirada.");
			return;
		}

		String caminho = caminhoDecodificado(request);
		if (caminhoProibido(caminho)) {
			escreverErro(response, HttpServletResponse.SC_FORBIDDEN,
					"Chave de API não pode gerenciar usuários, chaves de API, perfil ou sessões.");
			return;
		}
		// K4: o escopo decide antes do perfil. Mensagem própria, para quem integra saber que
		// o limite é da chave (e não do perfil) e criar outra chave com o escopo certo.
		if (!autenticada.get().permite(request.getMethod(), caminho)) {
			escreverErro(response, HttpServletResponse.SC_FORBIDDEN,
					"Esta chave não tem permissão para esta ação (escopo).");
			return;
		}

		Usuario dono = autenticada.get().dono();
		List<GrantedAuthority> autoridades = List.of(
				new SimpleGrantedAuthority("ROLE_" + dono.getPerfil().name()),
				new SimpleGrantedAuthority(AUTORIDADE));
		UsernamePasswordAuthenticationToken autenticacao = new UsernamePasswordAuthenticationToken(dono, null, autoridades);
		// K9: quem grava auditoria (o evento da ordem) precisa saber qual chave agiu.
		autenticacao.setDetails(new ChaveApiNaRequisicao(autenticada.get().chaveId(), autenticada.get().chaveNome()));
		SecurityContextHolder.getContext().setAuthentication(autenticacao);
		chain.doFilter(request, response);
	}

	/** Usado por SessaoAuthenticationFilter e CsrfProtectionFilter pra saber que a chave já decidiu. */
	static boolean autenticadoPorChave() {
		var autenticacao = SecurityContextHolder.getContext().getAuthentication();
		return autenticacao != null && autenticacao.getAuthorities().stream()
				.anyMatch(autoridade -> AUTORIDADE.equals(autoridade.getAuthority()));
	}

	// Caminho DECODIFICADO (o mesmo que o Spring MVC usa pra rotear): com o getRequestURI()
	// cru, "/%75suarios" ou "/chaves%2Dapi" escapavam da D7 e ainda assim chegavam no
	// controller — e "/%6Fperacoes/1" escaparia do escopo do mesmo jeito.
	private static String caminhoDecodificado(HttpServletRequest request) {
		return UrlPathHelper.defaultInstance.getPathWithinApplication(request);
	}

	private static boolean caminhoProibido(String caminho) {
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

package com.cambia.security;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

import com.cambia.auth.NomesCookieAuth;
import com.cambia.auth.SessaoRepository;
import com.cambia.usuario.Usuario;
import com.cambia.usuario.UsuarioRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class SessaoAuthenticationFilter extends OncePerRequestFilter {

	private final SessaoRepository sessaoRepository;
	private final UsuarioRepository usuarioRepository;

	SessaoAuthenticationFilter(SessaoRepository sessaoRepository, UsuarioRepository usuarioRepository) {
		this.sessaoRepository = sessaoRepository;
		this.usuarioRepository = usuarioRepository;
	}

	// Achado de revisão de segurança (Fase 3B): aceita o token tanto pelo header
	// Authorization (esquema original, usado em dev local sem o reverse-proxy HTTPS)
	// quanto pelo cookie httpOnly de sessão (produção) — os dois continuam funcionando,
	// nenhum substitui o outro.
	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String token = extrairTokenDoHeader(request);
		if (token == null) {
			token = extrairTokenDoCookie(request);
		}
		if (token != null) {
			autenticar(token);
		}
		chain.doFilter(request, response);
	}

	private String extrairTokenDoHeader(HttpServletRequest request) {
		String header = request.getHeader("Authorization");
		return header != null && header.startsWith("Bearer ") ? header.substring("Bearer ".length()) : null;
	}

	private String extrairTokenDoCookie(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (NomesCookieAuth.SESSAO.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

	private void autenticar(String token) {
		sessaoRepository.findByToken(token)
				.filter(sessao -> sessao.isValida(Instant.now()))
				.flatMap(sessao -> usuarioRepository.findById(sessao.getUsuarioId()))
				.ifPresent(this::definirAutenticacao);
	}

	private void definirAutenticacao(Usuario usuario) {
		List<GrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + usuario.getPerfil().name()));
		var authentication = new UsernamePasswordAuthenticationToken(usuario, null, authorities);
		SecurityContextHolder.getContext().setAuthentication(authentication);
	}

}

package com.cambia.auth;

import com.cambia.usuario.UsuarioRepository;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Achado de revisão de segurança: POST /auth/bootstrap-admin fazia "count() > 0, depois
 * insere" sem atomicidade — duas requisições concorrentes no instante em que ainda não
 * existe nenhum usuário podiam, as duas, passar pela checagem e criar um Admin cada.
 *
 * Testado aqui com Mockito, não com threads de verdade: a garantia de atomicidade vem da
 * chave primária no banco (bootstrap_lock, migração V17), não de timing — simular a
 * exceção que o banco lançaria pro "perdedor" da corrida (DataIntegrityViolationException
 * na trava) já prova que o código nunca chega a criar um segundo Admin nesse cenário,
 * sem depender de uma race condition real (e potencialmente instável) no teste.
 */
class AuthServiceBootstrapAdminTests {

	private final UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
	private final BootstrapLockRepository bootstrapLockRepository = mock(BootstrapLockRepository.class);
	private final AuthService service = new AuthService(usuarioRepository, mock(MagicLinkTokenRepository.class),
			mock(SessaoRepository.class), bootstrapLockRepository, mock(MagicLinkSender.class));

	private static final BootstrapAdminRequest REQUEST = new BootstrapAdminRequest("Admin", "admin@cambia.com.br");

	@Test
	void criaAdminQuandoConseguiuATravaENaoHaUsuario() {
		when(usuarioRepository.count()).thenReturn(0L);
		when(usuarioRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

		service.bootstrapAdmin(REQUEST);

		verify(bootstrapLockRepository).saveAndFlush(any());
		verify(usuarioRepository).save(any());
	}

	@Test
	void naoCriaSegundoAdminQuandoPerdeARaceCondition() {
		when(usuarioRepository.count()).thenReturn(0L);
		when(bootstrapLockRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

		ResponseStatusException erro = assertThrows(ResponseStatusException.class, () -> service.bootstrapAdmin(REQUEST));

		assertEquals(409, erro.getStatusCode().value());
		// o ponto central do teste: nunca chega a salvar um segundo Admin
		verify(usuarioRepository, never()).save(any());
	}

	@Test
	void naoTentaATravaSeJaExisteUsuarioCadastrado() {
		when(usuarioRepository.count()).thenReturn(1L);

		assertThrows(ResponseStatusException.class, () -> service.bootstrapAdmin(REQUEST));

		verify(bootstrapLockRepository, never()).saveAndFlush(any());
		verify(usuarioRepository, never()).save(any());
	}

}

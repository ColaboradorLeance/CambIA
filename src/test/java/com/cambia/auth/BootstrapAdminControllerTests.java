package com.cambia.auth;

import com.cambia.TestcontainersConfiguration;
import com.cambia.usuario.Perfil;
import com.cambia.usuario.UsuarioRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BootstrapAdminControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UsuarioRepository usuarioRepository;

	@Test
	void criaPrimeiroAdminQuandoNaoHaUsuarios() throws Exception {
		mockMvc.perform(post("/auth/bootstrap-admin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Admin Inicial\",\"email\":\"admin@cambia.com.br\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.email").value("admin@cambia.com.br"))
				.andExpect(jsonPath("$.perfil").value("ADMIN"));

		org.junit.jupiter.api.Assertions.assertEquals(Perfil.ADMIN,
				usuarioRepository.findByEmail("admin@cambia.com.br").orElseThrow().getPerfil());
	}

	@Test
	void falhaSeJaExisteAlgumUsuario() throws Exception {
		usuarioRepository.save(com.cambia.usuario.UsuarioTestFactory.novo("Já Existe", "existente@cambia.com.br", Perfil.ANALISTA));

		mockMvc.perform(post("/auth/bootstrap-admin")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"nome\":\"Admin Inicial\",\"email\":\"admin@cambia.com.br\"}"))
				.andExpect(status().isConflict());
	}

}

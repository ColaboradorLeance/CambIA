package com.cambia.chaveapi;

import java.sql.Date;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import javax.sql.DataSource;

import com.cambia.TestcontainersConfiguration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Incremento 81 (M1 de docs/chaves-api-usuario-plano.md, decisão K8): chaves criadas antes
 * da V27 (formato do Inc. 79, sem escopo e possivelmente sem expiração) continuam
 * funcionando, ganhando LEITURA + CRIAR_ORDENS e, se não tinham data, expiração = data da
 * migração + 1 ano. A expiração que já existia não muda.
 *
 * <p>Roda as migrações num schema próprio, parando na V26, insere chaves no formato antigo
 * e só então aplica a V27 — o banco dos demais testes já nasce na versão atual.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ChaveApiMigracaoTests {

	private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

	@Autowired
	private DataSource dataSource;

	private Flyway flyway(String schema, String alvo) {
		return Flyway.configure()
				.dataSource(dataSource)
				.schemas(schema)
				.defaultSchema(schema)
				.createSchemas(true)
				.locations("classpath:db/migration")
				.target(alvo)
				.load();
	}

	@Test
	void chaveAntigaGanhaEscoposEUmAno() {
		String schema = "m1_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
		JdbcTemplate jdbc = new JdbcTemplate(dataSource);
		try {
			flyway(schema, "26").migrate();

			Long tecnico = jdbc.queryForObject("INSERT INTO " + schema
					+ ".usuarios (nome, email, perfil) VALUES ('Integração Beyond', 'beyond@cambia.com.br', 'ANALISTA') RETURNING id",
					Long.class);
			Long admin = jdbc.queryForObject("INSERT INTO " + schema
					+ ".usuarios (nome, email, perfil) VALUES ('Admin', 'admin@cambia.com.br', 'ADMIN') RETURNING id",
					Long.class);
			LocalDate jaTinha = LocalDate.now(FUSO).plusDays(30);
			String insercao = "INSERT INTO " + schema + ".chaves_api (nome, usuario_id, prefixo, hash, criada_em, "
					+ "criada_por_usuario_id, expira_em) VALUES (?, ?, 'cambia_abcdef', ?, now(), ?, ?)";
			jdbc.update(insercao, "Sem expiração", tecnico, "a".repeat(64), admin, null);
			jdbc.update(insercao, "Com expiração", tecnico, "b".repeat(64), admin, Date.valueOf(jaTinha));

			LocalDate hoje = LocalDate.now(FUSO);
			flyway(schema, "27").migrate();

			for (String nome : new String[] { "Sem expiração", "Com expiração" }) {
				String escopos = jdbc.queryForObject("SELECT escopos FROM " + schema + ".chaves_api WHERE nome = ?",
						String.class, nome);
				assertTrue(escopos.contains("LEITURA") && escopos.contains("CRIAR_ORDENS"),
						"chave antiga sem os dois escopos: " + escopos);
			}
			LocalDate semData = jdbc.queryForObject("SELECT expira_em FROM " + schema
					+ ".chaves_api WHERE nome = 'Sem expiração'", LocalDate.class);
			assertEquals(hoje.plusYears(1), semData);
			LocalDate comData = jdbc.queryForObject("SELECT expira_em FROM " + schema
					+ ".chaves_api WHERE nome = 'Com expiração'", LocalDate.class);
			assertEquals(jaTinha, comData);
			// A coluna de rastreio existe e começa vazia (eventos antigos não vieram de chave).
			Integer colunas = jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema = ? "
					+ "AND table_name = 'operacoes_eventos' AND column_name = 'chave_api_id'", Integer.class, schema);
			assertEquals(1, colunas);
		} finally {
			jdbc.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
		}
	}

}

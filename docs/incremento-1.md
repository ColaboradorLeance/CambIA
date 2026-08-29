# Incremento 1 — Setup do projeto

Status: ✅ concluído

## O que foi feito

- Projeto Spring Boot 4.1.1 / Java 21, gerado via Spring Initializr, com Maven (usa o Maven Wrapper `mvnw`/`mvnw.cmd`, não é necessário ter Maven instalado).
- Dependências: Web (MVC), Data JPA, driver PostgreSQL, Actuator, e Testcontainers (para testes e para rodar localmente em desenvolvimento).
- PostgreSQL real via **Testcontainers** — tanto nos testes quanto ao rodar a aplicação localmente (`TestcontainersConfiguration`), então não é necessário ter um PostgreSQL instalado na máquina para desenvolver. **Único pré-requisito: Docker rodando.**
- Um teste de integração (`HealthCheckTests`) que sobe o contexto Spring completo com um Postgres real em container, bate no endpoint `GET /actuator/health` e confirma que a aplicação e a conexão com o banco estão `UP`.

## Como o TDD foi aplicado

1. **Red** — escrevi primeiro o teste `HealthCheckTests`, esperando `GET /actuator/health` retornar `status: UP` e `components.db.status: UP`. Rodei e ele falhou: o endpoint respondia 200, mas sem o campo `components` (Actuator não expõe detalhes por padrão).
2. **Green** — adicionei uma única linha de configuração (`management.endpoint.health.show-details=always` em `application.properties`). Rodei de novo e o teste passou.
3. **Refatorar** — nada a refatorar neste incremento (mudança mínima).
4. **Regressão** — rodei a suíte completa (`mvnw test`): 2 testes, 0 falhas (o teste de contexto gerado pelo Initializr + o novo `HealthCheckTests`).
5. Além dos testes, subi a aplicação de verdade (`mvnw spring-boot:test-run`, que usa o Postgres via Testcontainers) e bati no endpoint real com `curl` — respondeu 200 com o banco `UP`. Processo encerrado depois da verificação.

## Como rodar/testar você mesmo

```
./mvnw test                    # roda a suíte de testes (sobe um Postgres descartável via Testcontainers)
./mvnw spring-boot:test-run    # roda a aplicação localmente com Postgres via Testcontainers (Docker precisa estar rodando)
```
Com a aplicação rodando, `GET http://localhost:8080/actuator/health` deve retornar `status: UP`.

## O que NÃO está incluído neste incremento

- Nenhuma entidade de negócio (Cliente, Banco, Operação) — isso começa no Incremento 2.
- Configuração de conexão com um PostgreSQL real de produção/homologação (host, porta, credenciais do servidor on-premise) — ainda não foi definida. Isso será necessário antes do primeiro deploy, mas não bloqueia os próximos incrementos, que continuam usando Testcontainers em desenvolvimento e teste.

## Próximo passo
Incremento 2 — Cadastro de Cliente (CRUD).

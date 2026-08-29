# Incremento 3 — Cadastro de Banco (CRUD)

Status: ✅ concluído

## O que foi feito

- Entidade `Banco` (nome, percentual de comissão), pacote `com.cambia.banco`.
- Migration Flyway `V2__create_bancos_table.sql` criando a tabela `bancos`.
- CRUD completo via REST, no mesmo padrão do Cliente:
  - `POST /bancos` — cria banco, retorna `201` com `Location`
  - `GET /bancos/{id}` — busca por id, `404` se não existir
  - `GET /bancos` — lista todos
  - `PUT /bancos/{id}` — atualiza, `404` se não existir
  - `DELETE /bancos/{id}` — remove, retorna `204`
- `percentualComissao` representado como fração decimal (`0.70` = 70%), coerente com a fórmula confirmada em [dominio.md](dominio.md) (`Comissão Líquida = Total Bruto do Câmbio × percentual`).
- Validação: nome obrigatório; percentual obrigatório e limitado entre `0.0` e `1.0` (um percentual de comissão não pode ser negativo nem maior que 100% — checagem de sanidade, não uma regra de negócio inventada).

## Como o TDD foi aplicado

1. **Red** — escrevi `BancoControllerTests` (9 casos: criar+buscar, listar, atualizar, remover, 404, nome vazio, percentual negativo, percentual > 1, percentual ausente). Rodei: todos falharam com 404 (nada implementado ainda).
2. **Green** — implementei `Banco`, `BancoRepository`, `BancoRequest`/`BancoResponse`, `BancoService`, `BancoController`, seguindo a mesma estrutura já usada no Cliente. Rodei de novo: os 9 testes passaram de primeira (sem obstáculos técnicos desta vez, já que o padrão Flyway + camadas já estava resolvido no Incremento 2).
3. **Regressão** — suíte completa: 18 testes (Cliente + Banco + health-check + contexto), 0 falhas.
4. Validação manual: subi a aplicação de verdade, criei um cliente e um banco via `curl`, e listei — tudo funcionou.

## Como testar você mesmo
```
./mvnw spring-boot:test-run
```
```
curl -X POST http://localhost:8080/bancos -H "Content-Type: application/json" \
  -d '{"nome":"BZA","percentualComissao":0.70}'

curl http://localhost:8080/bancos
```

## O que NÃO está incluído
- Autenticação/autorização (entra no Incremento 4) — a API continua aberta.
- Regra de unicidade de nome do banco — não confirmada, não implementada.
- Qualquer forma de regra de comissão além de um percentual simples — o usuário confirmou que é sempre assim (ver [decisoes.md](decisoes.md)), então não há suporte a tabelas/faixas.

## Próximo passo
Incremento 4 — Autenticação e perfis (Admin / Usuário).

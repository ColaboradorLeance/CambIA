# Incremento 2 — Cadastro de Cliente (CRUD)

Status: ✅ concluído

## O que foi feito

- Entidade `Cliente` (nome, documento — CPF ou CNPJ, e-mail), pacote `com.cambia.cliente`.
- Migration Flyway `V1__create_clientes_table.sql` criando a tabela `clientes`.
- CRUD completo via REST:
  - `POST /clientes` — cria cliente, retorna `201` com `Location` do recurso
  - `GET /clientes/{id}` — busca por id, `404` se não existir
  - `GET /clientes` — lista todos
  - `PUT /clientes/{id}` — atualiza, `404` se não existir
  - `DELETE /clientes/{id}` — remove, retorna `204`
- Validação mínima: nome e documento obrigatórios (não vazios), e-mail obrigatório e em formato válido. **Nenhuma validação de formato de CPF/CNPJ (dígito verificador) foi implementada** — isso não estava confirmado como regra de negócio (ver [pendencias.md](pendencias.md)), então não foi suposto.

## Como o TDD foi aplicado

1. **Red** — escrevi `ClienteControllerTests` primeiro, cobrindo criar+buscar, listar, atualizar, remover, 404 para inexistente, e rejeição de cadastro inválido (nome vazio, e-mail mal formado). Rodei: todos os 7 testes falharam com 404 (endpoint não existia).
2. **Green** — implementei `Cliente` (entidade), `ClienteRepository`, `ClienteRequest`/`ClienteResponse` (DTOs com Bean Validation), `ClienteService` e `ClienteController`. Rodei de novo: os 7 testes passaram.
   - No meio do caminho apareceu um problema técnico: a migration do Flyway não estava rodando (Hibernate reclamava de tabela `clientes` inexistente). Causa: no Spring Boot 4, a auto-configuração do Flyway foi para um módulo próprio (`spring-boot-starter-flyway`), diferente do artefato `flyway-core` puro que eu tinha adicionado. Troquei para o starter correto e voltou a funcionar.
3. **Regressão** — suíte completa: 9 testes (contexto + health-check + Cliente), 0 falhas.
4. Validação manual: subi a aplicação de verdade e exercitei a API com `curl` (criar cliente, listar) — funcionou como esperado.

## Decisões técnicas tomadas neste incremento
- Schema gerenciado por **Flyway** (não mais Hibernate auto-ddl) — decisão registrada em [decisoes.md](decisoes.md), vale para todos os incrementos seguintes.
- Estrutura de pacotes por domínio (`com.cambia.cliente`).
- DTOs (records) separados da entidade JPA.

## Como testar você mesmo
```
./mvnw spring-boot:test-run
```
Depois, com a aplicação rodando:
```
curl -X POST http://localhost:8080/clientes -H "Content-Type: application/json" \
  -d '{"nome":"Empresa Exemplo LTDA","documento":"11.222.333/0001-44","email":"contato@exemplo.com"}'

curl http://localhost:8080/clientes
```

## O que NÃO está incluído
- Validação de formato de CPF/CNPJ (dígito verificador) ou distinção de regras entre PF e PJ — pendência #10.
- Autenticação/autorização — a API está aberta neste incremento; perfis (Admin/Usuário) entram no Incremento 4.
- Regra de unicidade de documento — não confirmada, não implementada.

## Atualização pós-entrega
O usuário confirmou que o Cliente **não precisa de e-mail** — campo removido por completo (entidade, DTOs e migration) logo após a entrega inicial deste incremento. Ciclo TDD refeito: testes atualizados primeiro (Red), depois entidade/DTOs/migration ajustados (Green), regressão confirmada (9 testes, 0 falhas). Cliente agora tem só Nome e Documento.

## Próximo passo
Incremento 3 — Cadastro de Banco (CRUD, com percentual de comissão).

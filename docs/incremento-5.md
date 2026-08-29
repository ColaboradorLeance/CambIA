# Incremento 5 — Registro de Operação (dados manuais)

Status: ✅ concluído

## O que foi feito

- Entidade `Operacao` com os campos manuais confirmados em [dominio.md](dominio.md): Data, Código do banco (opcional), Cliente, Banco, C/V, PR/CR/VIR, Moeda, Valor em ME, Spot Asset, Nivelamento, Taxa Final.
- **Sem cálculo automático ainda** — R$, Total Bruto do Câmbio e Comissão Líquida ficam para o Incremento 6.
- **ID do trade** gerado automaticamente no formato `AAAA-NNNNNN` (ano da operação + sequencial que reinicia a cada ano), conforme decidido no Incremento 4.
- CRUD parcial via REST, conforme escopo do roadmap ("criar/listar/visualizar"): `POST /operacoes`, `GET /operacoes/{id}`, `GET /operacoes`. **Sem PUT/DELETE** — editar ou excluir uma operação financeira depende de regras de status que ainda não temos (pendência #1), então não foi implementado para não inventar essa regra.
- Endpoint aberto para qualquer usuário autenticado (Admin ou Usuário) — diferente de Cliente/Banco/Usuário, que são exclusivos do Admin. Isso reflete a decisão já registrada de que "Usuário: realiza as operações do dia a dia".
- Validação: Cliente e Banco referenciados precisam existir (400 se não existirem); campos numéricos obrigatórios e positivos.

## Como as pendências de domínio foram destravadas sem inventar regra

Três pendências bloqueavam parcialmente este incremento (C/V, PR/CR/VIR, modelagem de moeda). Como este incremento **não faz nenhum cálculo** com esses campos — só armazena o que for digitado — resolvi de forma pragmática, sem fechar a regra de negócio:
- **C/V** e **PR/CR/VIR**: armazenados como texto livre (obrigatório, não vazio), sem validar contra uma lista fixa de valores. Se amanhã confirmarmos que só podem ser certos valores, adicionamos a validação depois sem precisar de migration.
- **Moeda**: armazenada como texto livre (ex: "USD"), sem cadastro/entidade própria. Se decidirmos por uma lista fixa (ISO 4217) ou cadastro administrável, isso é um ajuste de validação futuro, não uma mudança de schema.

Essas pendências continuam registradas em [pendencias.md](pendencias.md) — não foram "resolvidas", só deixaram de bloquear este incremento específico.

## Como o TDD foi aplicado

1. **Red** — `OperacaoControllerTests` (7 casos: criar+buscar, ID do trade sequencial dentro do ano, listar, 404, cliente inexistente, banco inexistente, campo obrigatório faltando). Rodei: todos falharam com 404 (nada implementado).
2. **Green** — migration, entidade, repositório, DTOs, serviço (com geração do ID do trade e validação de referências) e controller. Rodei de novo: os 7 testes passaram.
3. **Regressão** — suíte completa: 46 testes, 0 falhas.
4. Validação manual com **dados reais da planilha** (a linha da TLX usada na fase de descoberta): criei o cliente CRAS AGROINDUSTRIA LTDA, o banco TLX, e a operação com os valores reais (Valor em ME 885.242,40, Spot Asset 5,1990, Nivelamento 5,1960, Taxa Final 5,1856) — tudo funcionou, e o ID do trade `2026-000001` foi gerado corretamente.

## Detalhe técnico: acesso entre módulos
Para o serviço de Operação validar se o Cliente e o Banco informados existem, os repositórios `ClienteRepository` e `BancoRepository` (antes privados a cada pacote) foram tornados públicos. É um acoplamento esperado — Operação depende legitimamente de Cliente e Banco existirem.

## Como testar você mesmo
```
./mvnw spring-boot:test-run
```
Depois de logar (ver [incremento-4.md](incremento-4.md)) e ter um Cliente e um Banco cadastrados:
```
curl -X POST http://localhost:8080/operacoes -H "Authorization: Bearer SEU_TOKEN" -H "Content-Type: application/json" \
  -d '{"data":"2026-07-02","codigoBanco":"143258","clienteId":1,"bancoId":1,"cv":"V","prCrVir":"Credito","moeda":"USD","valorMe":885242.40,"spotAsset":5.1990,"nivelamento":5.1960,"taxaFinal":5.1856}'
```

## O que NÃO está incluído
- Cálculo automático (R$, Total Bruto do Câmbio, Comissão Líquida) — Incremento 6.
- Status da operação — Incremento 7, bloqueado pela pendência #1.
- Edição e exclusão de operações — dependem de regras de status ainda não definidas.
- Validação de domínio fechado para C/V, PR/CR/VIR e Moeda — continuam como texto livre.

## Próximo passo
Incremento 6 — Cálculo automático em tempo real (R$, Total Bruto do Câmbio, Comissão Líquida).

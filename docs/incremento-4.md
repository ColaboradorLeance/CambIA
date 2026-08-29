# Incremento 4 — Autenticação e perfis (Admin / Usuário)

Status: ✅ concluído

Este incremento acabou tendo mais partes que os anteriores porque a decisão de negócio (login sem senha, por link mágico) trouxe consigo várias peças técnicas. Foi dividido em 4 sub-etapas, cada uma com seu próprio ciclo Red-Green.

## 4.1 — Cadastro de Usuário (CRUD)

- Entidade `Usuario`: **Nome + E-mail** apenas (sem senha — decisão do usuário), mais o campo `Perfil` (`ADMIN` ou `USUARIO`).
- Migration `V3__create_usuarios_table.sql`, e-mail com constraint `UNIQUE` no banco (é a identidade de login).
- CRUD REST em `/usuarios`, mesmo padrão dos incrementos anteriores.
- Validação extra: e-mail duplicado retorna `409 Conflict` (necessário porque o e-mail é a chave de login — não é uma regra de negócio inventada, é consequência direta do mecanismo de autenticação).

## 4.2 — Login por link mágico

- `POST /auth/magic-link` `{email}` → gera um token de uso único (válido por 15 min), registra no banco e "envia" (ver nota abaixo) um link contendo o token. Sempre responde `202`, exista ou não o e-mail — evita expor quais e-mails estão cadastrados.
- `GET /auth/verify?token=...` → valida o token (existe, não expirado, não usado), marca como usado, e cria uma **sessão** (token opaco, válida por 8 horas), retornando `{ sessionToken, usuario }`.
- **Envio real de e-mail (SMTP) ainda não está configurado.** Por enquanto, o link é apenas registrado no log da aplicação (`LoggingMagicLinkSender`). Isso precisa ser resolvido antes de qualquer uso em produção — ver [pendencias.md](pendencias.md).

## 4.3 — Bootstrap do primeiro Admin

Durante a validação manual, descobri um problema real: se `/usuarios` exige perfil Admin para cadastrar qualquer usuário, **não existe forma de criar o primeiro Admin** em um banco recém-criado (ninguém tem sessão ainda para autorizar a criação do primeiro usuário).

Resolvido com um endpoint dedicado e público, mas auto-limitado:
- `POST /auth/bootstrap-admin` `{nome, email}` → cria o primeiro usuário como Admin, **somente se ainda não existir nenhum usuário cadastrado** (`409 Conflict` caso contrário).
- Depois de rodar uma vez, esse endpoint fica inútil (sempre retorna 409), então não representa uma porta aberta permanente.

## 4.4 — Proteção dos endpoints existentes

- Adicionado Spring Security com um filtro próprio (`SessaoAuthenticationFilter`) que lê o header `Authorization: Bearer <sessionToken>`, valida contra a tabela de sessões e autentica a requisição com a role correspondente ao perfil do usuário.
- Regras de acesso: `/auth/**` e `/actuator/health` públicos; `/clientes/**`, `/bancos/**` e `/usuarios/**` exigem perfil **Admin** (conforme já registrado em [decisoes.md](decisoes.md)); sem sessão válida → `401`; sessão válida mas perfil errado → `403`.
- Como isso mudou o comportamento de endpoints já entregues, os testes de Cliente, Banco e Usuário foram atualizados para autenticar como Admin antes de cada chamada — parte esperada do processo (mudar o teste primeiro para refletir o novo comportamento, depois confirmar que passa).

## Como o TDD foi aplicado (resumo das 4 sub-etapas)

Cada uma seguiu o mesmo ciclo: teste(s) escrito(s) primeiro (Red confirmado rodando e vendo falhar pelo motivo certo) → implementação mínima (Green) → regressão da suíte completa. Dois obstáculos técnicos reais apareceram e foram corrigidos no caminho:
1. Testes de CRUD compartilhavam o mesmo banco entre métodos de teste (sem rollback) — corrigido adicionando `@Transactional` às classes de teste de Cliente, Banco e Usuário.
2. O problema de bootstrap do primeiro Admin, descrito acima — descoberto na validação manual, não nos testes automatizados (nenhum teste tinha simulado "banco completamente vazio, sem sessão nenhuma disponível").

## Regressão final
**39 testes, 0 falhas** (contexto, health-check, Cliente, Banco, Usuário, Auth, Bootstrap, Security).

## Validação manual (fluxo completo)
1. `POST /auth/bootstrap-admin` → cria o primeiro Admin
2. `GET /clientes` sem sessão → `401`
3. `POST /auth/magic-link` → `202`, token aparece no log
4. `GET /auth/verify?token=...` → retorna `sessionToken`
5. `GET /clientes` com o `sessionToken` → `200`
6. `POST /clientes` com o `sessionToken` → `201`, cliente criado de verdade
7. Reusar o mesmo token de link mágico → `401` (uso único confirmado)

## Como testar você mesmo
```
./mvnw spring-boot:test-run
```
```
curl -X POST http://localhost:8080/auth/bootstrap-admin -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"seu-email@empresa.com"}'

curl -X POST http://localhost:8080/auth/magic-link -H "Content-Type: application/json" \
  -d '{"email":"seu-email@empresa.com"}'
```
Copie o token que aparecer no log da aplicação (`Link mágico para ...: /auth/verify?token=...`) e:
```
curl "http://localhost:8080/auth/verify?token=SEU_TOKEN"
```
Use o `sessionToken` retornado como `Authorization: Bearer <token>` nas chamadas a `/clientes`, `/bancos` e `/usuarios`.

## O que NÃO está incluído / pendências abertas
- **Envio real de e-mail (SMTP)** — o link mágico só é logado, não enviado de verdade. Precisa da configuração do servidor de e-mail da empresa antes de produção.
- Permissões finas por perfil além do "Admin vs. resto" macro (ex: o que exatamente um Usuário comum poderá fazer quando a Operação existir) — continua em aberto, será tratado quando chegarmos lá.
- Revogação de sessão (logout) — não implementado; sessões apenas expiram por tempo (8h).
- Duração dos tokens (15 min para o link, 8h para a sessão) é um parâmetro técnico razoável, não uma regra de negócio confirmada — pode ser ajustado depois se necessário.

## Próximo passo
Incremento 5 — Registro de Operação (dados manuais, sem cálculo ainda).

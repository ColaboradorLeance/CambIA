# Incremento 9 — Docker Compose (backend + frontend + Postgres)

Status: ✅ concluído

## O que foi feito

- `Dockerfile` (raiz) — build multi-stage do backend: compila com Maven, empacota num JRE enxuto (`eclipse-temurin:21-jre-alpine`).
- `frontend/Dockerfile` — build multi-stage do front-end: `npm run build` com Node, servido depois por Nginx (`frontend/nginx.conf` com fallback de rota para funcionar o roteamento do React Router).
- `docker-compose.yml` orquestrando os três serviços:
  - `db` — PostgreSQL 16 real (não Testcontainers), com volume nomeado (`db_data`) para persistir os dados entre reinícios, e healthcheck.
  - `backend` — só sobe depois que o banco está saudável (`depends_on: condition: service_healthy`), conectado via variáveis de ambiente (`SPRING_DATASOURCE_URL/USERNAME/PASSWORD`) — o Spring Boot lê essas variáveis automaticamente.
  - `frontend` — Nginx servindo os arquivos estáticos já compilados.
- `.dockerignore` (raiz e `frontend/`) para builds mais rápidos e enxutos.

## Por que isso também resolve uma pendência antiga
O [incremento-1.md](incremento-1.md) já apontava: "a conexão com um Postgres real de produção/homologação... ainda não foi definida". Agora existe — via variáveis de ambiente, sem hardcode. Isso não muda nada do ambiente de desenvolvimento (que continua usando Testcontainers normalmente, já que essas variáveis não são definidas quando você roda `mvnw test` ou `spring-boot:test-run`).

## Como foi validado
1. `docker compose build` — as duas imagens (backend, frontend) buildaram sem erro.
2. `docker compose up -d` — os três containers subiram, o backend só iniciou depois do Postgres real ficar saudável.
3. Confirmei `GET /actuator/health` do backend containerizado — `db: UP`, conectado ao Postgres real (versão 16.13), não ao Testcontainers.
4. Rodei o mesmo driver de navegador (Playwright) usado no Incremento 8, agora contra a stack 100% em containers: login por link mágico, criação de Cliente — tudo funcionou, sem erros de console.
5. **Testei persistência de verdade**: reiniciei todos os containers (`docker compose restart`) e o cliente cadastrado antes do restart continuou lá — confirma que o volume do Postgres está funcionando.

## Como usar
```
docker compose up -d --build
```
Acesse `http://localhost:5173` (frontend) — a API fica em `http://localhost:8080`. Para criar o primeiro Admin (banco novo/vazio):
```
curl -X POST http://localhost:8080/auth/bootstrap-admin -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"seu-email@empresa.com"}'
```

Para parar:
```
docker compose down          # para os containers, mantém os dados
docker compose down -v       # para os containers E apaga os dados (cuidado)
```

## O que NÃO está incluído
- **Credenciais do Postgres são fixas e fracas** (`cambia`/`cambia`), adequadas só para desenvolvimento/demonstração local. Antes de qualquer uso real (mesmo interno), isso precisa virar segredo de verdade (variável de ambiente vinda de fora do repositório, não do `docker-compose.yml`).
- Envio real de e-mail (SMTP) continua pendente — mesma limitação dos incrementos anteriores.
- Nenhuma configuração de HTTPS/TLS, reverse proxy, ou exposição segura para fora da rede local — isso é necessário antes de rodar em um servidor acessível de fora (ver decisão de hospedagem on-premise em [decisoes.md](decisoes.md)).

## Próximo passo
Depende de você: enviar a imagem da tela de Operação, definir escopo de Fechamento diário/Relatórios, ou revisar as credenciais/segurança antes de qualquer uso real.

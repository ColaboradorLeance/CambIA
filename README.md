# CambIA

Sistema interno para registrar e controlar operações de compra e venda de moedas internacionais (câmbio), substituindo o controle feito hoje em planilha. Uso interno, 10 a 50 usuários, hospedagem em servidor próprio (on-premise).

## Stack técnica

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, Spring Data JPA, Spring Security, Flyway |
| Banco de dados | PostgreSQL 16 |
| Front-end | React 19 (Vite), servido em produção via Nginx |
| Autenticação | Sem senha, por "magic link" (token enviado por e-mail) |
| Empacotamento | Docker + Docker Compose |

## O que já existe

Núcleo do backend completo (Cliente, Banco, Usuário, Autenticação, Operação com cálculo automático e status ANDAMENTO/CONFIRMADO/CANCELADO), Fechamento Diário (com export em PDF/Excel e job agendado), Relatórios (operações filtradas, comparativos, rankings, posição em aberto) e front-end cobrindo todas essas telas. Detalhes de cada incremento em [docs/roadmap.md](docs/roadmap.md); regras de negócio e fórmulas confirmadas em [docs/dominio.md](docs/dominio.md).

---

## Subindo o sistema com Docker Compose (caminho recomendado)

É a forma como o sistema é distribuído: três serviços (`backend`, `frontend`, `db`) mais um `mailpit` opcional para testar e-mail, todos definidos em [docker-compose.yml](docker-compose.yml).

### 1. Pré-requisitos na máquina/servidor

- [Docker Engine](https://docs.docker.com/engine/install/) e [Docker Compose plugin](https://docs.docker.com/compose/install/) (`docker compose version` deve funcionar; no Windows/Mac, instalar o Docker Desktop já traz os dois).
- Portas livres: `5173` (front-end), `8080` (API backend), `5432` (Postgres) e, se for usar o Mailpit, `8025`/`1025`.
- Git, para clonar o repositório.

### 2. Obter o código

```bash
git clone <url-do-repositorio>
cd CambIA
```

### 3. Configurar o `.env`

Copie o modelo e ajuste os valores antes de subir:

```bash
cp .env.example .env
```

Edite o `.env` com um editor de texto. **O mais importante para funcionar fora do `localhost` é `VITE_API_URL`** — leia a explicação abaixo antes de seguir. Os demais campos (envio de e-mail) já funcionam com um padrão razoável para começar a testar (link de acesso aparece no log do backend, sem precisar de SMTP configurado).

#### `VITE_API_URL` — o endereço que o navegador do usuário usa para falar com o backend

O front-end é um site estático: o endereço da API fica **gravado dentro dos arquivos JavaScript no momento do build** (`docker compose build`), não é lido em tempo de execução. Isso significa:

- **Testando na mesma máquina onde rodou o `docker compose up`**: não precisa mudar nada, o padrão `http://localhost:8080` já funciona, porque "localhost" ali é resolvido pelo navegador de quem está acessando.
- **Rodando num servidor e acessando de outras máquinas na rede** (o caso real de produção): `localhost` no navegador de quem acessa aponta pro próprio computador do usuário, não para o servidor — as chamadas à API vão falhar. É obrigatório definir no `.env`, **antes de buildar**:

  ```
  VITE_API_URL=http://IP-OU-DOMINIO-DO-SERVIDOR:8080
  ```

  Trocando `IP-OU-DOMINIO-DO-SERVIDOR` pelo endereço com que as máquinas dos usuários enxergam o servidor na rede (ex: `http://192.168.1.50:8080` ou um domínio interno).

- Se o valor de `VITE_API_URL` mudar depois, é preciso rebuildar o front-end (`docker compose up -d --build frontend`) — só reiniciar o container não é suficiente, pois o arquivo já foi gerado com o endereço antigo.

### 4. Subir os containers

```bash
docker compose up -d --build
```

Na primeira vez isso builda as imagens do backend (Maven + JDK 21) e do frontend (Node + Nginx), sobe o Postgres, aplica todas as migrações do Flyway automaticamente e inicia a aplicação. Acompanhe os logs até ver `Started CambIaApplication`:

```bash
docker compose logs -f backend
```

### 5. Verificar que subiu

```bash
curl http://localhost:8080/actuator/health
```

Deve responder `{"status":"UP", ...}` com o componente `db` também `UP`. O front-end fica em `http://localhost:5173` (ou `http://SEU_SERVIDOR:5173` a partir de outra máquina).

### 6. Criar o primeiro usuário (Admin)

O sistema não vem com nenhum usuário cadastrado. O primeiro Admin é criado por um endpoint que **só funciona uma única vez**, enquanto não existir nenhum usuário no banco:

```bash
curl -X POST http://localhost:8080/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```

Depois disso, esse endpoint passa a responder `409 Conflict` — novos usuários são criados de dentro do sistema, pela tela "Usuários", por um Admin já logado.

### 7. Fazer login (sem senha, por link mágico)

1. Acesse `http://localhost:5173` (ou o endereço do servidor) — vai redirecionar para `/login`.
2. Digite o e-mail cadastrado e clique em "Enviar link de acesso".
3. **Enquanto o envio real de e-mail (SMTP) não estiver configurado** (ver seção abaixo), o link não chega por e-mail de verdade — ele aparece no log do backend:

   ```bash
   docker compose logs backend | grep "Link mágico"
   ```

   Vai aparecer algo como `Link mágico para voce@suaempresa.com.br: /auth/verify?token=xxxxxxxx-...`. Copie só o valor depois de `token=`.
4. Cole esse valor no campo "Token" da tela e clique em "Entrar".

A sessão dura 8 horas (token opaco, sem cookies) e cada link mágico expira em 15 minutos ou no primeiro uso.

---

## Envio real de e-mail (opcional, mas necessário antes de usar em produção de verdade)

Sem isso configurado, todo login exige pegar o token no log do backend (aceitável para homologação/uso restrito, não recomendado para os usuários finais no dia a dia). Duas opções, documentadas lado a lado em [.env.example](.env.example):

- **Opção 1 — SMTP real** (ex: relay do Microsoft 365/Exchange Online): defina no `.env` `CAMBIA_MAIL_HABILITADO=true`, `CAMBIA_MAIL_REMETENTE`, `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`. Depois, `docker compose up -d` (não precisa rebuildar, são variáveis lidas em tempo de execução pelo backend).
- **Opção 2 — Mailpit** (teste local, sem enviar e-mail de verdade a ninguém): use os valores comentados no `.env.example` (`SPRING_MAIL_HOST=mailpit`, etc.) e acesse `http://localhost:8025` para ver os e-mails "enviados" capturados pelo Mailpit (que já sobe junto no `docker compose up`).

## Operações de manutenção

- **Parar tudo**: `docker compose down` (mantém os dados do Postgres, guardados no volume `db_data`).
- **Apagar tudo, incluindo o banco de dados**: `docker compose down -v` — **irreversível**, use só se realmente quiser zerar os dados.
- **Ver logs**: `docker compose logs -f backend` / `docker compose logs -f frontend`.
- **Backup do banco**: `docker compose exec db pg_dump -U cambia cambia > backup.sql` (rotina de backup automatizado ainda não faz parte do sistema — ver limitações abaixo).
- **Atualizar para uma nova versão do código**: `git pull` seguido de `docker compose up -d --build` (o Flyway aplica sozinho qualquer migração nova do banco).

---

## Rodando em desenvolvimento (sem Docker Compose, código-fonte direto)

Útil para desenvolver/depurar; para só usar o sistema em produção, prefira a seção do Docker Compose acima.

### Pré-requisitos

- Java 21 (JDK)
- Docker Engine rodando (o backend usa [Testcontainers](https://testcontainers.com/) para subir um Postgres descartável automaticamente em testes e no modo dev — **precisa do Docker mesmo fora do Compose**)
- Node.js 22+ e npm (para o front-end)

### Backend

```bash
./mvnw spring-boot:test-run
```

(No Windows, use `mvnw.cmd spring-boot:test-run`.) Isso sobe a API em `http://localhost:8080` contra um Postgres via Testcontainers, sem precisar configurar nada — mesmo comportamento usado nos testes automatizados.

Para rodar a suíte de testes:

```bash
./mvnw test
```

### Front-end

```bash
cd frontend
npm install
npm run dev
```

Sobe em `http://localhost:5173` com hot-reload, apontando por padrão para `http://localhost:8080` (ver `frontend/.env`, variável `VITE_API_URL` — aqui, ao contrário do build de produção, é só o valor lido pelo Vite em dev, pode editar e o hot-reload já aplica).

---

## Estrutura do projeto

```
src/main/java/com/cambia/    Backend (Spring Boot) — um pacote por domínio (cliente, banco, operacao, usuario, auth, security, web)
src/main/resources/db/migration/   Migrações Flyway (versionadas, nunca editar uma já aplicada)
src/test/java/com/cambia/    Testes (JUnit 5 + Testcontainers)
frontend/src/                Front-end (React + Vite)
docs/                        Documentação de domínio, decisões, pendências e roadmap (ver abaixo)
docker-compose.yml           Orquestração dos containers para rodar o sistema completo
```

## Documentação do projeto

- [docs/dominio.md](docs/dominio.md) — entidades, campos, o que é manual vs. calculado, fórmulas confirmadas e regras de negócio
- [docs/decisoes.md](docs/decisoes.md) — decisões arquiteturais e de produto já fechadas, incremento a incremento
- [docs/pendencias.md](docs/pendencias.md) — decisões de negócio ainda em aberto
- [docs/roadmap.md](docs/roadmap.md) — o que já foi entregue e o que falta

## Limitações conhecidas antes de um uso em produção "real"

- **Sem HTTPS/reverse proxy configurado.** O `docker-compose.yml` expõe a API e o front-end diretamente em HTTP. Para acesso de fora da rede local, é necessário colocar um reverse proxy (ex: Nginx ou Caddy na frente) com TLS antes de expor o servidor à internet.
- **Credenciais do Postgres fixas no `docker-compose.yml`** (`cambia`/`cambia`) — adequado para uma rede interna controlada; troque antes de expor o serviço a uma rede menos confiável.
- **Envio real de e-mail (SMTP)** depende de credenciais próprias da empresa (ver seção acima) — sem isso, login exige acesso aos logs do backend para pegar o token manualmente.
- Sem rotina automatizada de backup do banco — só o comando manual de `pg_dump` citado acima.

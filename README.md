# CambIA

Sistema para registrar e controlar operações de câmbio (compra/venda de moeda estrangeira), rodando **dentro da sua própria infraestrutura** — nenhum dado sai do seu servidor.

Cadastros (Clientes, Bancos, Usuários), Operações com cálculo financeiro automático, Fechamento Diário (com PDF/Excel e envio por e-mail), Relatórios e histórico de auditoria completo.

Backend, front-end e HTTPS vêm todos numa **única imagem Docker**. O único outro container é o banco de dados (PostgreSQL).

## Pré-requisitos

- Docker Engine + Docker Compose plugin instalados (`docker compose version` funcionando). [Docker Desktop](https://www.docker.com/products/docker-desktop/) no Windows/Mac já traz os dois.
- Portas `443` e `80` livres no servidor.
- **Token de acesso à imagem** (privada) — solicite a quem entregou o sistema.

> **PowerShell**: nos comandos que quebram linha com `\`, troque por `` ` `` (crase) no fim da linha, ou junte tudo numa linha só.

## Instalação

**1. Login no registro de imagens** (uma vez por máquina):

```bash
docker login ghcr.io -u <usuário-fornecido>
```
Quando pedir senha, cole o **token de acesso** fornecido.

**2. Crie uma pasta e, dentro dela, o `docker-compose.yml`:**

```yaml
services:
  db:
    image: postgres:16
    environment:
      POSTGRES_DB: cambia
      POSTGRES_USER: cambia
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-cambia}
    volumes:
      - db_data:/var/lib/postgresql/data
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U cambia"]
      interval: 5s
      timeout: 5s
      retries: 10

  app:
    image: ghcr.io/colaboradorleance/cambia:${CAMBIA_VERSION:-1.0.0}
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://db:5432/cambia}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-cambia}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-cambia}
      CERT_CN: ${CERT_CN:-localhost}
      CAMBIA_CORS_ORIGEM_ADICIONAL: https://${CERT_CN:-localhost}
      CAMBIA_MAIL_HABILITADO: ${CAMBIA_MAIL_HABILITADO:-false}
      CAMBIA_MAIL_REMETENTE: ${CAMBIA_MAIL_REMETENTE:-}
      SPRING_MAIL_HOST: ${SPRING_MAIL_HOST:-}
      SPRING_MAIL_PORT: ${SPRING_MAIL_PORT:-587}
      SPRING_MAIL_USERNAME: ${SPRING_MAIL_USERNAME:-}
      SPRING_MAIL_PASSWORD: ${SPRING_MAIL_PASSWORD:-}
      SPRING_MAIL_SMTP_AUTH: ${SPRING_MAIL_SMTP_AUTH:-true}
      SPRING_MAIL_SMTP_STARTTLS: ${SPRING_MAIL_SMTP_STARTTLS:-true}
    volumes:
      - app_certs:/etc/nginx/certs
    ports:
      - "443:8443"
      - "80:8080"
    depends_on:
      db:
        condition: service_healthy

volumes:
  db_data:
  app_certs:
```

**3. Na mesma pasta, crie o `.env`** (veja a tabela completa de variáveis mais abaixo):

```dotenv
CAMBIA_VERSION=1.0.0
POSTGRES_PASSWORD=troque-por-uma-senha-forte
CERT_CN=localhost

# Deixe em branco por enquanto — preencha só ao seguir a seção "E-mail" abaixo.
# Preencher SPRING_MAIL_HOST sem o resto da configuração faz até o healthcheck falhar.
CAMBIA_MAIL_HABILITADO=false
CAMBIA_MAIL_REMETENTE=
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

**4. (Opcional) Banco de dados já existente**: se sua empresa já tem um Postgres, remova o serviço `db` inteiro (e a linha `db_data:` de `volumes:` e o `depends_on:` do `app`) do `docker-compose.yml`, e defina no `.env`:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://SEU_HOST:5432/SEU_BANCO
SPRING_DATASOURCE_USERNAME=seu_usuario
POSTGRES_PASSWORD=sua_senha
```
(o usuário do banco precisa poder criar tabelas na primeira subida — é quando as migrações são aplicadas automaticamente, sem passo manual nenhum.)

**5. Suba tudo:**

```bash
docker compose pull
docker compose up -d
docker compose logs -f app   # acompanhe até aparecer "Started CambIaApplication"
```

**6. Verifique:**

```bash
curl -k https://localhost/actuator/health
# {"status":"UP"}
```
(o `-k` é só pra linha de comando, por causa do certificado autoassinado — ver seção abaixo). O sistema fica em `https://localhost` (ou `https://SEU_SERVIDOR`).

**7. Crie o primeiro usuário (Admin)** — só funciona uma vez, enquanto não existir nenhum usuário:

```bash
curl -k -X POST https://localhost/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```
Depois disso, novos usuários são criados pela tela "Usuários", por um Admin logado.

**8. Faça login** — sem senha, por link mágico: acesse `https://localhost`, digite o e-mail cadastrado e clique em "Enviar link de acesso". Enquanto o e-mail (próxima seção) não estiver configurado, pegue o token no log:

```bash
docker compose logs app | grep "Link mágico"
```
Copie o valor depois de `token=` e cole no campo "Token" da tela. Sessão dura 8h; o link expira em 15min ou no primeiro uso.

## Variáveis de ambiente

Todas ficam no `.env`, na mesma pasta do `docker-compose.yml`.

| Variável | Padrão | Para quê |
|---|---|---|
| `CAMBIA_VERSION` | `1.0.0` | Versão/tag da imagem a rodar — ver "Atualizar versão" abaixo. |
| `POSTGRES_PASSWORD` | `cambia` | Senha do banco. **Troque em produção** — a porta `5432` fica publicada no host. |
| `CERT_CN` | `localhost` | Nome/IP gravado no certificado HTTPS — precisa bater com o endereço real do servidor. |
| `CAMBIA_MAIL_HABILITADO` | `false` | Liga o envio real de e-mail. Sem isso, login depende do log (ver acima). |
| `CAMBIA_MAIL_REMETENTE` | — | E-mail exibido como remetente das mensagens. |
| `SPRING_MAIL_HOST` | — | Servidor SMTP. Para Microsoft 365: `smtp.office365.com`. |
| `SPRING_MAIL_PORT` | `587` | Porta do servidor SMTP. |
| `SPRING_MAIL_USERNAME` | — | Caixa de e-mail usada para autenticar no SMTP. |
| `SPRING_MAIL_PASSWORD` | — | Senha (ou senha de aplicativo) dessa caixa. |
| `SPRING_MAIL_SMTP_AUTH` | `true` | Autenticação SMTP — deixe `true` para Microsoft 365. |
| `SPRING_MAIL_SMTP_STARTTLS` | `true` | STARTTLS — deixe `true` para Microsoft 365. |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/cambia` | Só se usar banco de dados externo (ver passo 4). |
| `SPRING_DATASOURCE_USERNAME` | `cambia` | Só se usar banco de dados externo. |

## E-mail (Microsoft 365 / Exchange Online)

**Necessário antes de usar em produção de verdade** — sem isso, login sempre depende do log. No `.env`:

```dotenv
CAMBIA_MAIL_HABILITADO=true
CAMBIA_MAIL_REMETENTE=nao-responda@suaempresa.com.br
SPRING_MAIL_HOST=smtp.office365.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=nao-responda@suaempresa.com.br
SPRING_MAIL_PASSWORD=<senha-da-caixa-ou-senha-de-aplicativo>
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

Depois de preencher: `docker compose up -d` (não precisa baixar imagem nova, `up -d` já aplica).

> ⚠️ Tenants Microsoft 365 recentes vêm com **SMTP AUTH desabilitado** por padrão nas caixas de e-mail — sem habilitar, a autenticação falha mesmo com senha certa. Um admin do M365 habilita em: Centro de administração do Exchange → Destinatários → a caixa usada em `SPRING_MAIL_USERNAME` → Email apps → **"Authenticated SMTP"** (ou via PowerShell: `Set-CASMailbox -Identity usuario@suaempresa.com.br -SmtpClientAuthenticationDisabled $false`). Se a caixa tiver MFA ativado, use uma **senha de aplicativo** em vez da senha normal.

## Certificado HTTPS

O HTTPS já vem embutido na imagem — gera um certificado autoassinado sozinho na primeira subida (guardado num volume, não é regerado depois). O navegador mostra um aviso de "conexão não segura" na primeira visita (esperado — é autoassinado, mas o tráfego é criptografado normalmente); aceite para continuar.

Se for acessar de **outras máquinas na rede** (não só localhost), defina `CERT_CN` no `.env` **antes da primeira subida** com o IP/domínio real (ex: `192.168.1.50`). Pra trocar depois de já ter subido: `docker compose down && docker volume rm cambia_app_certs` (confira o nome exato com `docker volume ls`) e suba de novo.

## Atualizar versão

```bash
# no .env, troque:
CAMBIA_VERSION=1.1.0
# depois:
docker compose pull
docker compose up -d
```
Migrações de banco novas (se houver) aplicam sozinhas; dados existentes são preservados. Versão em uso: `docker compose images`.

## Comandos úteis

```bash
docker compose logs -f app              # logs em tempo real
docker compose ps                       # status dos containers
docker compose up -d                    # reaplica mudanças no .env (restart sozinho NÃO relê o .env)
docker compose down                     # parar tudo (mantém os dados)
docker compose down -v                  # apagar TUDO, incluindo o banco — irreversível
docker compose exec db pg_dump -U cambia cambia > backup.sql        # backup
docker compose exec -T db psql -U cambia cambia < backup.sql        # restaurar
```

## Problemas comuns

| Sintoma | Causa provável / o que fazer |
|---|---|
| `app` fica `unhealthy` ou `/actuator/health` não retorna `UP` | `SPRING_MAIL_HOST` preenchido sem o resto do e-mail configurado — o healthcheck tenta autenticar de verdade. Preencha tudo (seção "E-mail") ou deixe `SPRING_MAIL_HOST` em branco. |
| Banco não acessível (`ECONNREFUSED`/`ETIMEDOUT`) | Verifique `docker compose ps` (deve mostrar `db` `healthy`); com banco externo, teste `pg_isready -h SEU_HOST -p 5432` e confira firewall/`pg_hba.conf`. |
| Login não funciona / sessão some | Acesse por `https://`, não `http://`; confirme que `CERT_CN` bate com o endereço usado no navegador. |
| `429 Muitas tentativas` | Limite de tentativas de login — espere alguns minutos. |
| Link mágico não chega | Confira `SPRING_MAIL_*` no `.env`; SMTP recusando autenticação quase sempre é o SMTP AUTH desligado no M365 (ver seção "E-mail"); enquanto isso, use `docker compose logs app \| grep "Link mágico"`. |
| `409 Conflict` ao criar o primeiro Admin | Já existe usuário no banco — crie novos pela tela "Usuários". |
| `401`/`denied` no `docker login`/`pull` | Credenciais expiradas/incorretas — peça a quem entregou o sistema. |
| `500 Internal Server Error` genérico | Mensagem é sempre genérica de propósito; veja a causa real em `docker compose logs app`. |

## Histórico de versões

| Versão | Data | Destaques |
|---|---|---|
| v1.0.0 | 2026-09-03 | Primeiro release — cadastros, operações de câmbio com cálculo automático, fechamento diário (PDF/Excel), relatórios, auditoria. Empacotado como imagem única. |

## Suporte

Dúvidas na instalação, erros, ou para solicitar credenciais/nova versão: entre em contato com quem entregou o sistema.

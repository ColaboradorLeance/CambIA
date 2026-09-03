# CambIA — Deploy

Instruções para rodar o sistema CambIA usando a imagem Docker oficial.

Sistema para registrar e controlar operações de câmbio (compra/venda de moeda estrangeira), rodando **dentro da sua própria infraestrutura** — nenhum dado sai do seu servidor. Cadastros, Operações com cálculo financeiro automático, Fechamento Diário (PDF/Excel), Relatórios e histórico de auditoria.

---

## Imagem

| Tag | Quando usar |
|---|---|
| `ghcr.io/colaboradorleance/cambia:latest` | Sempre a versão mais recente publicada — útil só para testar antes de decidir fixar uma versão. |
| `ghcr.io/colaboradorleance/cambia:1.0.0` | Versão exata, imutável — **recomendada para produção**, fixa exatamente o que está rodando. |

Portas expostas: **443** (HTTPS) e **80** (redireciona para HTTPS) — backend, front-end e HTTPS vêm todos juntos nesta imagem. O único outro container necessário é o banco de dados.

---

## Pré-requisitos

- Docker instalado (`docker --version`)
- Token de acesso à imagem — solicite a quem entregou o sistema (a imagem é privada)

> **Windows (PowerShell):** os comandos abaixo mostram a sintaxe Linux (`\` para quebrar linha). No PowerShell use `` ` `` (backtick) no lugar de `\`.

---

## Instalação

### 1. Autenticar no registro de imagens

```bash
docker login ghcr.io -u <usuário-fornecido>
```

Quando pedir senha, cole o **token de acesso** fornecido. Só precisa fazer isso uma vez por máquina.

---

### 2. Criar o arquivo de variáveis de ambiente

Crie uma pasta para o sistema e, dentro dela, um arquivo `.env` com o conteúdo abaixo:

```env
# Banco de dados — ver "Banco de dados" no passo 3 para os valores certos de cada opção
SPRING_DATASOURCE_URL=jdbc:postgresql://cambia-db:5432/cambia
SPRING_DATASOURCE_USERNAME=cambia
SPRING_DATASOURCE_PASSWORD=SENHA_SEGURA

# Nome/IP do servidor gravado no certificado HTTPS — ver seção "Certificado HTTPS"
CERT_CN=localhost
CAMBIA_CORS_ORIGEM_ADICIONAL=https://localhost

# Envio do link mágico de login por e-mail — deixe em branco por enquanto, preencha só
# ao seguir a seção "E-mail" mais abaixo (preencher o host sem o resto configurado faz
# até a checagem de saúde do sistema falhar)
CAMBIA_MAIL_HABILITADO=false
CAMBIA_MAIL_REMETENTE=
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

Veja a tabela completa de variáveis mais abaixo.

---

### 3. Banco de dados — escolha uma opção

#### Opção A — Banco via Docker (sem PostgreSQL instalado)

Crie uma rede isolada e suba um container PostgreSQL:

**Linux / macOS (bash):**
```bash
docker network create cambia-net

docker run -d \
  --name cambia-db \
  --network cambia-net \
  -e POSTGRES_USER=cambia \
  -e POSTGRES_PASSWORD=SENHA_SEGURA \
  -e POSTGRES_DB=cambia \
  -v cambia-pgdata:/var/lib/postgresql/data \
  postgres:16
```

**Windows (PowerShell):**
```powershell
docker network create cambia-net

docker run -d `
  --name cambia-db `
  --network cambia-net `
  -e POSTGRES_USER=cambia `
  -e POSTGRES_PASSWORD=SENHA_SEGURA `
  -e POSTGRES_DB=cambia `
  -v cambia-pgdata:/var/lib/postgresql/data `
  postgres:16
```

Confirme no `.env` (passo 2) que `SPRING_DATASOURCE_URL=jdbc:postgresql://cambia-db:5432/cambia` e que `SPRING_DATASOURCE_PASSWORD` bate com a `SENHA_SEGURA` usada acima.

> Os dados ficam no volume `cambia-pgdata` e persistem mesmo que o container seja recriado.

#### Opção B — Banco próprio já existente

No `.env`, aponte para o seu servidor:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://seu-servidor.host.com:5432/cambia
SPRING_DATASOURCE_USERNAME=cambia
SPRING_DATASOURCE_PASSWORD=sua_senha
```

> O usuário do banco precisa ter permissão `CREATE` no schema `public` para as migrações serem aplicadas na primeira subida (automático, sem comando manual — ver passo 4). Depois da primeira subida, pode revogar essa permissão até a próxima atualização que traga uma migração nova.

---

### 4. Subir o container da aplicação

Diferente de sistemas que exigem rodar um comando de migração à parte antes de subir, o CambIA aplica as migrações do banco **sozinho, automaticamente, ao iniciar** — não existe passo manual aqui, é só subir:

**Opção A — banco via Docker** (inclui `--network`):

```bash
# Linux / macOS
docker run -d \
  --name cambia \
  --network cambia-net \
  --restart unless-stopped \
  -p 443:8443 \
  -p 80:8080 \
  -v cambia-certs:/etc/nginx/certs \
  --env-file .env \
  ghcr.io/colaboradorleance/cambia:1.0.0
```

```powershell
# Windows (PowerShell)
docker run -d `
  --name cambia `
  --network cambia-net `
  --restart unless-stopped `
  -p 443:8443 `
  -p 80:8080 `
  -v cambia-certs:/etc/nginx/certs `
  --env-file .env `
  ghcr.io/colaboradorleance/cambia:1.0.0
```

**Opção B — banco próprio** (sem `--network`):

```bash
docker run -d \
  --name cambia \
  --restart unless-stopped \
  -p 443:8443 \
  -p 80:8080 \
  -v cambia-certs:/etc/nginx/certs \
  --env-file .env \
  ghcr.io/colaboradorleance/cambia:1.0.0
```

Acompanhe os logs até aparecer `Started CambIaApplication`:

```bash
docker logs cambia -f
```

### 5. Verificar

```bash
curl -k https://localhost/actuator/health
# {"status":"UP"}
```

(o `-k` ignora o aviso do certificado autoassinado — só para linha de comando; ver seção abaixo). O sistema fica em `https://localhost` (ou `https://SEU_SERVIDOR`).

---

## Certificado HTTPS

Diferente de sistemas que exigem um proxy reverso próprio (nginx/Traefik/Caddy) na frente, **o CambIA já vem com o HTTPS embutido** na imagem — gera um certificado autoassinado sozinho na primeira subida (guardado no volume `cambia-certs`, não é regerado depois). Não é preciso instalar nem configurar nenhum proxy externo.

O navegador mostra um aviso de "conexão não segura" na primeira visita — esperado, é autoassinado, mas o tráfego é criptografado normalmente; aceite para continuar.

Se for acessar de **outras máquinas na rede** (não só localhost), defina `CERT_CN` e `CAMBIA_CORS_ORIGEM_ADICIONAL` no `.env` **antes da primeira subida**, com o IP/domínio real (ex: `CERT_CN=192.168.1.50`, `CAMBIA_CORS_ORIGEM_ADICIONAL=https://192.168.1.50`). Para trocar depois de já ter subido:

```bash
docker stop cambia && docker rm cambia && docker volume rm cambia-certs
# rode novamente o comando do passo 4
```

> `docker restart` **não relê o `.env`** — sempre recrie o container (`docker stop` → `docker rm` → `docker run`) depois de alterar variáveis.

---

## Primeiro acesso

O sistema não vem com nenhum usuário cadastrado. Diferente de sistemas que criam um Admin sozinhos a partir de uma senha em variável de ambiente, o CambIA **não tem senha nenhuma** — login é só por link mágico por e-mail.

**1. Criar o primeiro usuário (Admin)** — funciona uma única vez, enquanto não existir nenhum usuário no banco:

```bash
curl -k -X POST https://localhost/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```

Depois disso, esse endpoint responde `409 Conflict` — novos usuários são criados pela tela "Usuários", por um Admin já logado.

**2. Fazer login**: acesse `https://localhost`, digite o e-mail cadastrado e clique em "Enviar link de acesso". Enquanto o e-mail (seção abaixo) não estiver configurado, pegue o token no log:

```bash
docker logs cambia | grep "Link mágico"
```

Copie o valor depois de `token=` e cole no campo "Token" da tela. Sessão dura 8h; o link expira em 15min ou no primeiro uso.

---

## Variáveis de ambiente

Todas ficam no arquivo `.env` (passo 2).

| Variável | Descrição |
|---|---|
| `SPRING_DATASOURCE_URL` | String de conexão JDBC do banco — ver "Banco de dados". |
| `SPRING_DATASOURCE_USERNAME` | Usuário do banco. |
| `SPRING_DATASOURCE_PASSWORD` | Senha do banco. |
| `CERT_CN` | Nome/IP gravado no certificado HTTPS — precisa bater com o endereço real do servidor. |
| `CAMBIA_CORS_ORIGEM_ADICIONAL` | `https://` + o mesmo valor de `CERT_CN`. |
| `CAMBIA_MAIL_HABILITADO` | `true` liga o envio real de e-mail. Sem isso, login depende do log. |
| `CAMBIA_MAIL_REMETENTE` | E-mail exibido como remetente das mensagens. |
| `SPRING_MAIL_HOST` | Servidor SMTP. Para Microsoft 365: `smtp.office365.com`. |
| `SPRING_MAIL_PORT` | Porta do servidor SMTP (`587` para Microsoft 365). |
| `SPRING_MAIL_USERNAME` | Caixa de e-mail usada para autenticar no SMTP. |
| `SPRING_MAIL_PASSWORD` | Senha (ou senha de aplicativo) dessa caixa. |
| `SPRING_MAIL_SMTP_AUTH` | Autenticação SMTP — `true` para Microsoft 365. |
| `SPRING_MAIL_SMTP_STARTTLS` | STARTTLS — `true` para Microsoft 365. |

---

## E-mail (Microsoft 365 / Exchange Online)

**Necessário antes de usar em produção de verdade** — sem isso, login sempre depende do log. No `.env`:

```env
CAMBIA_MAIL_HABILITADO=true
CAMBIA_MAIL_REMETENTE=nao-responda@suaempresa.com.br
SPRING_MAIL_HOST=smtp.office365.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=nao-responda@suaempresa.com.br
SPRING_MAIL_PASSWORD=<senha-da-caixa-ou-senha-de-aplicativo>
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

Depois de preencher, recrie o container (`docker stop cambia && docker rm cambia`, rode de novo o comando do passo 4 — `--env-file .env` lê os valores atuais do arquivo).

> ⚠️ Tenants Microsoft 365 recentes vêm com **SMTP AUTH desabilitado** por padrão nas caixas de e-mail — sem habilitar, a autenticação falha mesmo com senha certa. Um admin do M365 habilita em: Centro de administração do Exchange → Destinatários → a caixa usada em `SPRING_MAIL_USERNAME` → Email apps → **"Authenticated SMTP"** (ou via PowerShell: `Set-CASMailbox -Identity usuario@suaempresa.com.br -SmtpClientAuthenticationDisabled $false`). Se a caixa tiver MFA ativado, use uma **senha de aplicativo** em vez da senha normal.

---

## Atualizar para nova versão

```bash
# 1. Baixar a nova imagem
docker pull ghcr.io/colaboradorleance/cambia:1.1.0

# 2. Reiniciar o container
docker stop cambia && docker rm cambia
# Rode novamente o comando do passo 4, trocando a tag da imagem para :1.1.0
```

Migrações de banco novas (se houver) são aplicadas sozinhas na subida — sem passo manual. Dados existentes são preservados.

---

## Comandos úteis

```bash
# Logs em tempo real
docker logs cambia -f

# Últimas 100 linhas de log
docker logs cambia --tail 100

# Status dos containers
docker ps

# Reiniciar o app (não relê o .env — use só para reiniciar o processo)
docker restart cambia

# Parar e remover o app (não apaga dados do banco)
docker stop cambia && docker rm cambia

# Parar e remover o banco (apaga dados se não usar volume)
docker stop cambia-db && docker rm cambia-db

# Backup do banco
docker exec cambia-db pg_dump -U cambia cambia > backup.sql

# Restaurar um backup (com o sistema parado ou o banco vazio)
cat backup.sql | docker exec -i cambia-db psql -U cambia cambia

# Ver dados do volume (não remove nada)
docker volume inspect cambia-pgdata
```

---

## Solução de problemas

### Banco não acessível — `ECONNREFUSED` ou `connect ETIMEDOUT`

O container da aplicação não consegue alcançar o banco.

**Se estiver usando banco via Docker (Opção A):**
- Confirme que o container `cambia-db` está rodando: `docker ps`
- Confirme que `cambia` e `cambia-db` usam `--network cambia-net`
- Verifique se `SPRING_DATASOURCE_URL` usa `cambia-db` como host (nome do container, não IP)

**Se estiver usando banco próprio (Opção B):**
- Confirme que o servidor está acessível: `pg_isready -h SEU_HOST -p 5432`
- Verifique se o PostgreSQL aceita conexões externas (`listen_addresses = '*'` no `postgresql.conf`)
- Verifique regras de firewall liberando a porta 5432

### `docker ps` mostra `cambia` saudável, mas `/actuator/health` não retorna `UP`

Quase sempre é `SPRING_MAIL_HOST` preenchido no `.env` sem o resto da configuração de e-mail — a checagem de saúde tenta autenticar de verdade e falha, mesmo com `CAMBIA_MAIL_HABILITADO=false`. Preencha tudo (seção "E-mail") ou deixe `SPRING_MAIL_HOST` em branco.

### Login não funciona / sessão some

- Acesse por `https://`, não `http://`.
- Confirme que `CERT_CN` e `CAMBIA_CORS_ORIGEM_ADICIONAL` batem com o endereço usado no navegador.
- `429 Muitas tentativas`: limite de tentativas de login — espere alguns minutos.

### O link mágico não chega no e-mail

- Confirme os campos `SPRING_MAIL_*` no `.env` (ver "E-mail" acima).
- Autenticação SMTP recusada mesmo com usuário/senha corretos: quase sempre é o SMTP AUTH desligado no M365 — veja o aviso na seção "E-mail".
- Enquanto isso não estiver resolvido: `docker logs cambia | grep "Link mágico"`.

### `409 Conflict` ao criar o primeiro Admin

Já existe usuário no banco — esse endpoint só funciona uma vez, de propósito. Crie novos usuários pela tela "Usuários".

### `docker login` ou `docker pull` retornam `401 Unauthorized` / `denied`

Credenciais expiradas ou incorretas — peça a quem entregou o sistema para confirmar/renovar o acesso.

### Erro genérico / `500 Internal Server Error`

A mensagem é sempre genérica de propósito (a causa real nunca é exposta, por segurança) — veja a causa completa em `docker logs cambia`.

---

## Histórico de versões

| Versão | Tag Docker | Data | Destaques |
|---|---|---|---|
| v1.0.0 | `:latest` `:1.0.0` | 2026-09-03 | Primeiro release — cadastros, operações de câmbio com cálculo automático, fechamento diário (PDF/Excel), relatórios, auditoria. |

---

## Notas

- **Dados**: ficam no PostgreSQL que você gerencia. O container `cambia` é stateless, exceto pelo volume do certificado (`cambia-certs`).
- **Segredos**: nunca compartilhe o `.env`. Trate `SPRING_DATASOURCE_PASSWORD`, a senha do Microsoft 365 e o token de acesso ao registro de imagens como senhas.
- **Suporte**: dúvidas na instalação, erros, ou para solicitar credenciais/nova versão — entre em contato com quem entregou o sistema.

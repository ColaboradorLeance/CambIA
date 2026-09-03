# CambIA

Instruções para instalar e rodar o sistema CambIA usando as imagens Docker oficiais.

> Este documento é para quem vai **rodar o sistema** (cliente final). Se você é desenvolvedor do CambIA e precisa do código-fonte, veja [DEVELOPMENT.md](DEVELOPMENT.md).

Sistema para registrar e controlar operações de compra e venda de moedas internacionais (câmbio), rodando **dentro da sua própria infraestrutura** (on-premise) — nenhum dado sai do seu servidor.

## O que o sistema oferece

- **Cadastros**: Clientes, Bancos (com fórmula de comissão própria por banco) e Usuários (perfis Admin / Analista / Consultor).
- **Operações de câmbio**: registro, edição, confirmação e cancelamento, com cálculo automático de todos os valores financeiros no momento da confirmação (R$, Total Bruto, Comissão, Spread, Custo, Rebate, etc.).
- **Fechamento Diário**: resumo operacional e financeiro de qualquer data, com exportação em PDF e Excel, e envio automático por e-mail num horário configurável.
- **Relatórios**: operações filtradas, comparativo entre períodos, rankings (por cliente, banco, moeda, usuário) e posição em aberto.
- **Histórico de auditoria**: todo evento de uma operação (criação, edição, confirmação, cancelamento) fica registrado com autor e data/hora.

## Imagens

O CambIA é composto por **3 imagens próprias**, sempre publicadas juntas com o mesmo número de versão (mais o PostgreSQL, que é a imagem oficial de terceiros, não nossa):

| Imagem | Tags disponíveis | Quando usar |
|---|---|---|
| `ghcr.io/colaboradorleance/cambia-backend` | `:1.0.0`, `:latest` | `:X.Y.Z` (versão exata, imutável) — **recomendada para produção**, fixa exatamente o que está rodando. `:latest` sempre aponta para a versão mais recente publicada — útil só para testar antes de decidir fixar uma versão. |
| `ghcr.io/colaboradorleance/cambia-frontend` | `:1.0.0`, `:latest` | mesma lógica acima |
| `ghcr.io/colaboradorleance/cambia-reverse-proxy` | `:1.0.0`, `:latest` | mesma lógica acima |

**Nunca misture tags de versões diferentes entre as três** — elas são desenvolvidas e testadas sempre em conjunto.

**Portas expostas**: `443` (HTTPS, ponto de entrada principal) e `80` (só redireciona para HTTPS). Não existe porta separada para o front-end — front-end, backend e API ficam todos atrás do `reverse-proxy`, na mesma origem.

## Pré-requisitos

- Docker Engine e Docker Compose plugin instalados (`docker compose version` deve funcionar). No Windows/Mac, o [Docker Desktop](https://www.docker.com/products/docker-desktop/) já traz os dois; em Linux, veja o [guia oficial](https://docs.docker.com/engine/install/).
- Portas `443` e `80` livres no servidor (e `5432` se for usar o Postgres incluso e quiser acessá-lo de outra máquina — ver "Banco de dados" abaixo).
- **Token de acesso ao registro de imagens** — solicite a quem entregou o sistema. As imagens são **privadas**, não públicas.

> **Windows (PowerShell)**: os comandos deste guia usam sintaxe de shell Unix (bash). A maioria funciona sem alteração no PowerShell — a única diferença aparece em comandos que quebram linha com `\`: no PowerShell, troque `\` por `` ` `` (crase) no fim da linha, ou junte tudo numa linha só.

---

## Instalação

### 1. Autenticar no registro de imagens

```bash
docker login ghcr.io -u <usuário-fornecido>
```

Vai pedir uma senha — cole o **token de acesso** fornecido (não é a senha de uma conta comum). Essa autenticação fica salva localmente; não precisa repetir a cada atualização, só se trocar de servidor/máquina.

### 2. Criar a pasta do sistema e o `docker-compose.yml`

Crie uma pasta para o sistema (ex: `cambia/`) e, dentro dela, um arquivo `docker-compose.yml` com o conteúdo abaixo:

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

  backend:
    image: ghcr.io/colaboradorleance/cambia-backend:${CAMBIA_VERSION:-1.0.0}
    environment:
      SPRING_DATASOURCE_URL: ${SPRING_DATASOURCE_URL:-jdbc:postgresql://db:5432/cambia}
      SPRING_DATASOURCE_USERNAME: ${SPRING_DATASOURCE_USERNAME:-cambia}
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:-cambia}
      CAMBIA_MAIL_HABILITADO: ${CAMBIA_MAIL_HABILITADO:-false}
      CAMBIA_MAIL_REMETENTE: ${CAMBIA_MAIL_REMETENTE:-}
      SPRING_MAIL_HOST: ${SPRING_MAIL_HOST:-}
      SPRING_MAIL_PORT: ${SPRING_MAIL_PORT:-587}
      SPRING_MAIL_USERNAME: ${SPRING_MAIL_USERNAME:-}
      SPRING_MAIL_PASSWORD: ${SPRING_MAIL_PASSWORD:-}
      SPRING_MAIL_SMTP_AUTH: ${SPRING_MAIL_SMTP_AUTH:-true}
      SPRING_MAIL_SMTP_STARTTLS: ${SPRING_MAIL_SMTP_STARTTLS:-true}
      CAMBIA_CORS_ORIGEM_ADICIONAL: https://${CERT_CN:-localhost}
    depends_on:
      db:
        condition: service_healthy

  frontend:
    image: ghcr.io/colaboradorleance/cambia-frontend:${CAMBIA_VERSION:-1.0.0}
    depends_on:
      - backend

  reverse-proxy:
    image: ghcr.io/colaboradorleance/cambia-reverse-proxy:${CAMBIA_VERSION:-1.0.0}
    environment:
      CERT_CN: ${CERT_CN:-localhost}
    volumes:
      - reverse_proxy_certs:/etc/nginx/certs
    ports:
      - "443:8443"
      - "80:8080"
    depends_on:
      - backend
      - frontend

volumes:
  db_data:
  reverse_proxy_certs:
```

`backend`, `frontend` e `reverse-proxy` usam `image:` (baixados prontos) — nenhum código-fonte é necessário nesta máquina. `db` usa a imagem oficial do Postgres, baixada direto do Docker Hub.

### 3. Configurar o `.env`

Na mesma pasta, crie um arquivo `.env`:

```dotenv
# Versão do sistema a usar — combina com a tag das imagens acima.
# Ver "Atualizando para uma nova versão" antes de mudar este valor.
CAMBIA_VERSION=1.0.0

# Senha do banco de dados — TROQUE por uma senha forte antes de rodar em produção.
POSTGRES_PASSWORD=

# Nome/IP do servidor gravado no certificado HTTPS — ver seção própria abaixo.
CERT_CN=localhost

# Envio do link mágico de login por e-mail — ver "Envio real de e-mail" abaixo.
CAMBIA_MAIL_HABILITADO=false
CAMBIA_MAIL_REMETENTE=
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

Todos os campos têm um valor padrão que já funciona para testar — a tabela completa, incluindo o que é recomendado mudar antes de produção, está na seção "Variáveis de ambiente" mais abaixo.

### 4. Banco de dados — escolha uma opção

**Opção A — Postgres incluso (padrão, mais simples)**: nada a fazer — o serviço `db` do `docker-compose.yml` do passo 2 já cuida disso. Os dados ficam no volume `db_data`, persistindo mesmo que os containers sejam recriados.

**Opção B — usar um Postgres que sua empresa já tem**: remova o serviço `db` inteiro (e a linha `db_data:` de `volumes:`) do `docker-compose.yml`, e troque o `depends_on:` do `backend` por nada (remova essas duas linhas também). No `.env`, adicione:

```dotenv
SPRING_DATASOURCE_URL=jdbc:postgresql://SEU_HOST:5432/SEU_BANCO
SPRING_DATASOURCE_USERNAME=seu_usuario
POSTGRES_PASSWORD=sua_senha
```

(sim, `POSTGRES_PASSWORD` continua sendo a variável usada — mesmo sem o serviço `db`, é ela que o `backend` usa como senha do banco de dados.)

> O usuário do banco precisa poder criar tabelas no schema `public` na primeira subida — é quando o Flyway aplica as migrações automaticamente (ver próxima seção). Depois da primeira subida, esse privilégio pode ser revogado até a próxima atualização de versão que traga uma migração nova.

### 5. Migrações do banco de dados

Diferente de sistemas que exigem rodar um comando de migração à parte, o CambIA aplica as migrações do banco **sozinho, automaticamente, toda vez que o backend inicia** (via Flyway) — não existe nenhum passo manual aqui. Isso acontece já no próximo passo, ao subir os containers.

### 6. Subir os containers

```bash
docker compose pull
docker compose up -d
```

O primeiro comando baixa as imagens; o segundo sobe tudo, aplica as migrações do banco e inicia a aplicação. Acompanhe os logs até ver `Started CambIaApplication`:

```bash
docker compose logs -f backend
```

### 7. Verificar

```bash
curl -k https://localhost/actuator/health
# {"status":"UP"}
```

(o `-k` ignora o aviso do certificado autoassinado — só para linha de comando; no navegador, ver a seção de certificado abaixo). O sistema inteiro fica em `https://localhost` (ou `https://SEU_SERVIDOR`, a partir de outra máquina).

---

## Proxy reverso e certificado HTTPS — já incluso, nada a configurar

Diferente de sistemas que esperam você colocar um nginx/Traefik/Caddy próprio na frente, **o CambIA já vem com o seu próprio reverse-proxy** — um dos três containers do passo 2. HTTPS liga sozinho, com um certificado autoassinado gerado automaticamente na primeira subida (guardado num volume Docker, não é regerado nas próximas vezes). Não é preciso instalar nem configurar nenhum proxy externo para o sistema funcionar.

### `CERT_CN` — o nome/IP do servidor gravado no certificado

O nome/IP gravado no certificado precisa bater com o endereço que o navegador vai usar para acessar:

- **Testando na mesma máquina**: não precisa mudar nada, o padrão `CERT_CN=localhost` já funciona.
- **Rodando num servidor e acessando de outras máquinas na rede**: defina no `.env`, **antes da primeira subida**:

  ```dotenv
  CERT_CN=IP-OU-DOMINIO-DO-SERVIDOR
  ```

  Trocando pelo endereço com que as máquinas dos usuários enxergam o servidor (ex: `192.168.1.50` ou um domínio interno). Se precisar trocar depois de já ter subido uma vez, apague o volume do certificado para forçar gerar um novo: `docker compose down && docker volume rm cambia_reverse_proxy_certs` (o nome exato do volume pode variar — confira com `docker volume ls`).

- **Aviso de "conexão não segura"**: o navegador vai mostrar esse aviso na primeira visita — é esperado (o certificado é autoassinado, não emitido por uma autoridade confiável). O tráfego continua criptografado normalmente; aceite o aviso para continuar. Se sua empresa tiver uma CA própria, ou quiser eliminar esse aviso, é possível trocar por um certificado real — consulte quem entregou o sistema.
- **Quer usar seu próprio proxy reverso na frente mesmo assim** (ex: para centralizar TLS de vários sistemas atrás de um Traefik/nginx corporativo já existente)? Aponte-o para as portas `8443`/`8080` do container `reverse-proxy` do CambIA (não remova esse container — ele também faz o roteamento interno entre front-end e backend) e faça esse proxy externo terminar TLS antes. Consulte o suporte para orientação nesse cenário.

## Primeiro acesso

O sistema não vem com nenhum usuário cadastrado — diferente de sistemas que criam um Admin sozinhos na primeira inicialização a partir de uma senha definida em variável de ambiente, o CambIA não tem senha nenhuma (autenticação é só por link mágico), então esse primeiro usuário é criado explicitamente por você, chamando o endpoint abaixo.

### 1. Criar o primeiro usuário (Admin)

O primeiro Admin é criado por um endpoint que **só funciona uma única vez**, enquanto não existir nenhum usuário no banco:

```bash
curl -k -X POST https://localhost/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```

> **PowerShell**: troque as barras `\` do fim de cada linha por crases `` ` ``, ou escreva tudo numa linha só.

Depois disso, esse endpoint passa a responder `409 Conflict` — novos usuários são criados de dentro do sistema, pela tela "Usuários", por um Admin já logado.

### 2. Fazer login (sem senha, por link mágico)

1. Acesse `https://localhost` (ou o endereço do servidor) — vai redirecionar para `/login`. Aceite o aviso do certificado autoassinado na primeira visita.
2. Digite o e-mail cadastrado e clique em "Enviar link de acesso".
3. **Enquanto o envio real de e-mail (SMTP) não estiver configurado** (próxima seção), o link não chega por e-mail de verdade — ele aparece no log do backend:

   ```bash
   docker compose logs backend | grep "Link mágico"
   ```

   Vai aparecer algo como `Link mágico para voce@suaempresa.com.br: /auth/verify?token=xxxxxxxx-...`. Copie só o valor depois de `token=`.
4. Cole esse valor no campo "Token" da tela e clique em "Entrar".

A sessão dura 8 horas — fica guardada num cookie `httpOnly`, não acessível por JavaScript; cada link mágico expira em 15 minutos ou no primeiro uso. Tentativas repetidas de login (por e-mail ou por IP) têm um limite — depois de algumas tentativas seguidas, é preciso esperar alguns minutos.

---

## Envio real de e-mail

**Necessário antes de usar o sistema em produção de verdade.** Sem isso configurado, todo login exige pegar o token no log do backend (aceitável para homologação/teste inicial, não para os usuários finais no dia a dia). No `.env`, preencha:

```dotenv
CAMBIA_MAIL_HABILITADO=true
CAMBIA_MAIL_REMETENTE=nao-responda@suaempresa.com.br
SPRING_MAIL_HOST=smtp.office365.com
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=nao-responda@suaempresa.com.br
SPRING_MAIL_PASSWORD=<senha-ou-token-do-relay-smtp>
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

`SPRING_MAIL_HOST`/`PORT` acima são o exemplo do relay SMTP do Microsoft 365 / Exchange Online, mas funciona com qualquer servidor SMTP autenticado — ajuste conforme as credenciais reais da sua empresa. Depois de preencher, aplique com:

```bash
docker compose up -d
```

Não precisa baixar imagem nova nem recriar containers do zero — são variáveis lidas em tempo de execução pelo backend; `up -d` já detecta a mudança e reinicia só o necessário.

### Testar o envio de e-mail antes de configurar o SMTP real (opcional)

Para ver como os e-mails do sistema ficam sem enviar nada de verdade, é possível usar um capturador de e-mail local (Mailpit) durante a fase de testes. No `docker-compose.yml`, adicione este serviço **dentro de `services:`, no mesmo nível de `db`/`backend`/`frontend`/`reverse-proxy`** (não dentro de `volumes:`, que fica só depois):

```yaml
services:
  # ... db, backend, frontend, reverse-proxy já existentes ...

  mailpit:
    image: axllent/mailpit:latest
    ports:
      - "8025:8025"
      - "1025:1025"

volumes:
  # ... db_data, reverse_proxy_certs já existentes ...
```

E no `.env`:

```dotenv
CAMBIA_MAIL_HABILITADO=true
SPRING_MAIL_HOST=mailpit
SPRING_MAIL_PORT=1025
SPRING_MAIL_SMTP_AUTH=false
SPRING_MAIL_SMTP_STARTTLS=false
```

Depois de `docker compose up -d`, acesse `http://localhost:8025` para ver os e-mails "enviados" pelo sistema. **Não use isso em produção** — é só para conferir o conteúdo do e-mail antes de configurar um SMTP de verdade.

---

## Variáveis de ambiente

Todas ficam no `.env`, na mesma pasta do `docker-compose.yml`.

### Recomendado alterar antes de produção real

| Variável | Padrão | Descrição |
|---|---|---|
| `POSTGRES_PASSWORD` | `cambia` | Senha do banco de dados. **Troque por uma senha forte** — o padrão é só para testar, e a porta `5432` fica publicada no host. |
| `CERT_CN` | `localhost` | Nome/IP gravado no certificado HTTPS — precisa bater com o endereço real do servidor (ver seção própria acima). |
| `CAMBIA_MAIL_HABILITADO` + `SPRING_MAIL_*` | desligado | Envio real de e-mail — sem isso, login depende de olhar o log do backend (ver seção própria acima). |

### Controle de versão

| Variável | Padrão | Descrição |
|---|---|---|
| `CAMBIA_VERSION` | `1.0.0` | Tag de versão usada nas 3 imagens do sistema — ver "Atualizando para uma nova versão" abaixo. |

### Banco de dados externo (só se estiver usando a Opção B)

| Variável | Padrão | Descrição |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://db:5432/cambia` | String de conexão JDBC completa do seu Postgres. |
| `SPRING_DATASOURCE_USERNAME` | `cambia` | Usuário do banco. |

(a senha do banco externo usa a mesma variável `POSTGRES_PASSWORD` da tabela acima.)

## Atualizando para uma nova versão

Cada versão nova do sistema é anunciada com o número dela (ex: `1.1.0`). Para atualizar:

```bash
# 1. Edite o .env e troque:
CAMBIA_VERSION=1.1.0

# 2. Baixe a nova versão das imagens e reinicie:
docker compose pull
docker compose up -d
```

As migrações de banco de dados novas (se houver) são aplicadas automaticamente na subida — não é preciso nenhum passo manual no banco. Os dados existentes (clientes, bancos, operações, usuários) são preservados normalmente.

Para conferir a versão que está rodando: `docker compose images` mostra a tag de cada imagem em uso.

## Comandos úteis

```bash
# Logs em tempo real (troque "backend" por "frontend"/"reverse-proxy"/"db" conforme o caso)
docker compose logs -f backend

# Últimas 100 linhas de log
docker compose logs backend --tail 100

# Status dos containers
docker compose ps

# Reiniciar tudo (não relê o .env por si só — "up -d" detecta e aplica mudanças; "restart" não)
docker compose restart

# Parar tudo (mantém os dados do Postgres, guardados no volume db_data)
docker compose down

# Apagar tudo, incluindo o banco de dados — IRREVERSÍVEL, use só se realmente quiser zerar os dados
docker compose down -v

# Backup do banco
docker compose exec db pg_dump -U cambia cambia > backup.sql

# Restaurar um backup (com o sistema parado ou o banco vazio)
docker compose exec -T db psql -U cambia cambia < backup.sql

# Ver onde ficam os dados do volume (não remove nada)
docker volume inspect cambia_db_data
```

## Solução de problemas

### Banco não acessível (`ECONNREFUSED` / `connect ETIMEDOUT` nos logs do backend)

Se estiver usando a **Opção A** (Postgres incluso):
- Confirme que o container `db` está rodando e saudável: `docker compose ps` (deve mostrar `healthy`).
- Veja os logs dele: `docker compose logs db`.

Se estiver usando a **Opção B** (banco próprio):
- Confirme que o servidor está acessível a partir de onde o Docker roda: `pg_isready -h SEU_HOST -p 5432` (rode de dentro de um container, ou da própria máquina do servidor).
- Verifique se o Postgres aceita conexões externas (`listen_addresses` no `postgresql.conf`, e a entrada correspondente no `pg_hba.conf`).
- Verifique regras de firewall liberando a porta usada.

### Login não funciona / a sessão "some" depois de logar

O navegador está rejeitando o cookie de sessão — quase sempre é um problema de HTTPS/certificado, já que o cookie exige conexão segura:

- Confirme que está acessando por `https://`, não `http://`.
- Se o `CERT_CN` não bate com o endereço que você está usando no navegador (ex: `CERT_CN=localhost` mas acessando por IP), o certificado é rejeitado de um jeito que pode até impedir o cookie de ser aceito em alguns navegadores — ajuste `CERT_CN` (ver seção própria acima) e refaça a subida.
- Tentativas repetidas de login demais em pouco tempo acionam um limite de segurança (`429 Muitas tentativas`) — espere alguns minutos.

### O link mágico não chega no e-mail

- Confirme que `CAMBIA_MAIL_HABILITADO=true` e os campos `SPRING_MAIL_*` estão preenchidos corretamente no `.env` (ver "Envio real de e-mail" acima).
- Veja os logs do backend por erros de envio: `docker compose logs backend | grep -i mail`.
- Enquanto isso não estiver resolvido, o link continua disponível no log: `docker compose logs backend | grep "Link mágico"`.

### `409 Conflict` ao tentar criar o primeiro Admin

Já existe pelo menos um usuário cadastrado no banco — esse endpoint só funciona uma única vez, de propósito. Peça a um Admin já existente para criar o novo usuário pela tela "Usuários", ou confirme que não subiu por engano um banco que já tinha dados de uma instalação anterior.

### `docker login` ou `docker pull` retornam `401 Unauthorized` / `denied`

As credenciais fornecidas expiraram ou estão incorretas — entre em contato com quem entregou o sistema para confirmar/renovar o acesso ao registro de imagens.

### Erro genérico / `500 Internal Server Error`

A mensagem de erro mostrada é sempre genérica de propósito (a causa real nunca é exposta ao navegador, por segurança) — a causa completa fica só no log do backend: `docker compose logs backend`. Se precisar de ajuda, inclua esse trecho do log ao contatar o suporte.

---

## Histórico de versões

| Versão | Tag Docker | Data | Destaques |
|---|---|---|---|
| v1.0.0 | `:latest` `:1.0.0` | 2026-09-03 | Primeiro release oficial — cadastros (Clientes/Bancos/Usuários), Operações de câmbio com cálculo automático completo, Fechamento Diário com exportação em PDF/Excel, Relatórios e histórico de auditoria. |

## Limitações conhecidas

- **Certificado HTTPS autoassinado por padrão** — funciona (o tráfego é criptografado de verdade), mas o navegador mostra um aviso na primeira visita. Ver seção de certificado acima para detalhes e como trocar por um real.
- **Senha do Postgres definida por você** (`POSTGRES_PASSWORD`) — use uma senha forte; a porta `5432` fica publicada no host por padrão.
- **Sem rotina automatizada de backup do banco** — use o comando manual de `pg_dump` citado acima, agendado externamente (ex: `cron`).

## Notas

- **Dados**: ficam no PostgreSQL que você gerencia (volume `db_data`, se estiver usando a Opção A). Os containers `backend`/`frontend`/`reverse-proxy` são stateless, exceto pelo volume do certificado.
- **Segredos**: nunca compartilhe o `.env`. Trate `POSTGRES_PASSWORD`, as credenciais SMTP e o token de acesso ao registro de imagens como senhas.

## Suporte

Em caso de dúvida na instalação, erro inesperado, ou para solicitar credenciais/uma nova versão, entre em contato com quem entregou o sistema.

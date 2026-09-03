# CambIA

Sistema para registrar e controlar operações de compra e venda de moedas internacionais (câmbio), rodando **dentro da sua própria infraestrutura** (on-premise) — nenhum dado sai do seu servidor. Este guia cobre a instalação, configuração e manutenção do sistema a partir das imagens Docker fornecidas.

> Este documento é para quem vai **rodar o sistema**. Se você é desenvolvedor do CambIA e precisa do código-fonte, veja [DEVELOPMENT.md](DEVELOPMENT.md).

## Stack técnica

| Camada | Tecnologia |
|---|---|
| Backend | Java 21, Spring Boot 4.1.1, PostgreSQL 16 |
| Front-end | React 19, servido via Nginx |
| Autenticação | Sem senha, por "link mágico" (token enviado por e-mail) |
| Transporte | HTTPS obrigatório, único ponto de entrada exposto |
| Empacotamento | Docker + Docker Compose |

## O que o sistema oferece

- **Cadastros**: Clientes, Bancos (com fórmula de comissão própria por banco) e Usuários (perfis Admin / Analista / Consultor).
- **Operações de câmbio**: registro, edição, confirmação e cancelamento, com cálculo automático de todos os valores financeiros no momento da confirmação (R$, Total Bruto, Comissão, Spread, Custo, Rebate, etc.).
- **Fechamento Diário**: resumo operacional e financeiro de qualquer data, com exportação em PDF e Excel, e envio automático por e-mail num horário configurável.
- **Relatórios**: operações filtradas, comparativo entre períodos, rankings (por cliente, banco, moeda, usuário) e posição em aberto.
- **Histórico de auditoria**: todo evento de uma operação (criação, edição, confirmação, cancelamento) fica registrado com autor e data/hora.

---

## 1. Pré-requisitos

- **Docker Engine** e **Docker Compose plugin** na máquina/servidor onde o sistema vai rodar — `docker compose version` precisa funcionar. No Windows/Mac, instalar o [Docker Desktop](https://www.docker.com/products/docker-desktop/) já traz os dois; em Linux, siga o [guia oficial do Docker Engine](https://docs.docker.com/engine/install/).
- **Portas livres** no servidor: `443` e `80` (acesso HTTPS ao sistema) e `5432` (Postgres, só necessário se algo além do próprio sistema precisar acessar o banco diretamente).
- **Credenciais de acesso às imagens**, fornecidas por quem entregou o sistema (ver próxima seção) — as imagens são privadas, não públicas.

## 2. Autenticar no repositório de imagens

As imagens do CambIA ficam num repositório **privado** (GitHub Container Registry). Antes de baixá-las pela primeira vez, autentique com as credenciais que foram enviadas junto com este guia:

```bash
docker login ghcr.io -u <usuário-fornecido>
```

Vai pedir uma senha — cole o **token de acesso** fornecido (não é uma senha de conta comum). Essa autenticação fica salva localmente; não precisa repetir a cada vez que for atualizar o sistema, só se trocar de servidor/máquina.

## 3. Criar o arquivo `docker-compose.yml`

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
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/cambia
      SPRING_DATASOURCE_USERNAME: cambia
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

Note que `backend`, `frontend` e `reverse-proxy` usam `image:` (baixados prontos), não `build:` — nada de código-fonte é necessário nesta máquina. `db` usa a imagem oficial do Postgres, baixada direto do Docker Hub.

## 4. Configurar o `.env`

Na mesma pasta do `docker-compose.yml`, crie um arquivo `.env`:

```dotenv
# Versão do sistema a usar — combina com a tag das imagens acima.
# Ver a seção "Atualizando para uma nova versão" antes de mudar este valor.
CAMBIA_VERSION=1.0.0

# Senha do banco de dados — TROQUE por uma senha forte antes de rodar em produção.
POSTGRES_PASSWORD=

# Nome/IP do servidor gravado no certificado HTTPS — ver explicação abaixo.
CERT_CN=localhost

# Envio do link mágico de login por e-mail — ver seção "Envio real de e-mail" abaixo.
CAMBIA_MAIL_HABILITADO=false
CAMBIA_MAIL_REMETENTE=
SPRING_MAIL_HOST=
SPRING_MAIL_PORT=587
SPRING_MAIL_USERNAME=
SPRING_MAIL_PASSWORD=
SPRING_MAIL_SMTP_AUTH=true
SPRING_MAIL_SMTP_STARTTLS=true
```

### `CERT_CN` — o nome/IP do servidor gravado no certificado HTTPS

O `reverse-proxy` gera um certificado autoassinado sozinho na primeira subida (guardado num volume Docker, não é regerado nas próximas vezes). O nome/IP nele precisa bater com o endereço que o navegador vai usar para acessar:

- **Testando na mesma máquina**: não precisa mudar nada, o padrão `CERT_CN=localhost` já funciona.
- **Rodando num servidor e acessando de outras máquinas na rede**: defina no `.env`, **antes da primeira subida**:

  ```dotenv
  CERT_CN=IP-OU-DOMINIO-DO-SERVIDOR
  ```

  Trocando pelo endereço com que as máquinas dos usuários enxergam o servidor na rede (ex: `192.168.1.50` ou um domínio interno). Se precisar trocar depois de já ter subido uma vez, apague o volume do certificado para forçar gerar um novo: `docker compose down && docker volume rm cambia_reverse_proxy_certs` (o nome exato do volume pode variar — confira com `docker volume ls`).

- **Certificado autoassinado**: o navegador vai mostrar um aviso de "conexão não segura" na primeira visita — é esperado (não é um certificado emitido por uma autoridade confiável), aceite o aviso para continuar. Se sua empresa tiver uma CA própria ou um domínio público de verdade, é possível trocar por um certificado real — consulte quem entregou o sistema.

## 5. Subir os containers

```bash
docker compose pull
docker compose up -d
```

O primeiro comando baixa as imagens (backend, frontend, reverse-proxy e o Postgres oficial); o segundo sobe tudo, aplica automaticamente as migrações de banco de dados e inicia a aplicação. Acompanhe os logs até ver `Started CambIaApplication`:

```bash
docker compose logs -f backend
```

## 6. Verificar que subiu

```bash
curl -k https://localhost/actuator/health
```

(o `-k` ignora o aviso do certificado autoassinado — só para linha de comando; no navegador, ver nota sobre `CERT_CN` acima). Deve responder `{"status":"UP"}`. O sistema inteiro fica em `https://localhost` (ou `https://SEU_SERVIDOR`, a partir de outra máquina) — não existe porta separada para o front-end.

## 7. Criar o primeiro usuário (Admin)

O sistema não vem com nenhum usuário cadastrado. O primeiro Admin é criado por um endpoint que **só funciona uma única vez**, enquanto não existir nenhum usuário no banco:

```bash
curl -k -X POST https://localhost/auth/bootstrap-admin \
  -H "Content-Type: application/json" \
  -d '{"nome":"Seu Nome","email":"voce@suaempresa.com.br"}'
```

Depois disso, esse endpoint passa a responder `409 Conflict` — novos usuários são criados de dentro do sistema, pela tela "Usuários", por um Admin já logado.

## 8. Fazer login (sem senha, por link mágico)

1. Acesse `https://localhost` (ou o endereço do servidor) — vai redirecionar para `/login`. Aceite o aviso do certificado autoassinado na primeira visita.
2. Digite o e-mail cadastrado e clique em "Enviar link de acesso".
3. **Enquanto o envio real de e-mail (SMTP) não estiver configurado** (ver seção abaixo), o link não chega por e-mail de verdade — ele aparece no log do backend:

   ```bash
   docker compose logs backend | grep "Link mágico"
   ```

   Vai aparecer algo como `Link mágico para voce@suaempresa.com.br: /auth/verify?token=xxxxxxxx-...`. Copie só o valor depois de `token=`.
4. Cole esse valor no campo "Token" da tela e clique em "Entrar".

A sessão dura 8 horas — fica guardada num cookie `httpOnly`, não acessível por JavaScript; cada link mágico expira em 15 minutos ou no primeiro uso. Tentativas repetidas de login (por e-mail ou por IP) têm um limite — depois de algumas tentativas seguidas, é preciso esperar alguns minutos.

---

## Envio real de e-mail (necessário antes de usar em produção de verdade)

Sem isso configurado, todo login exige pegar o token no log do backend (aceitável para homologação/teste inicial, não recomendado para os usuários finais no dia a dia). No `.env`, preencha:

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

`SPRING_MAIL_HOST`/`PORT` acima são o exemplo do relay SMTP do Microsoft 365 / Exchange Online, mas funciona com qualquer servidor SMTP autenticado da sua empresa — ajuste conforme suas credenciais reais. Depois de preencher, aplique com:

```bash
docker compose up -d
```

(não precisa baixar imagem nova — são variáveis lidas em tempo de execução pelo backend.)

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

## Operações de manutenção

- **Parar tudo**: `docker compose down` (mantém os dados do Postgres, guardados no volume `db_data`).
- **Apagar tudo, incluindo o banco de dados**: `docker compose down -v` — **irreversível**, use só se realmente quiser zerar os dados.
- **Ver logs**: `docker compose logs -f backend` / `docker compose logs -f frontend`.
- **Backup do banco**: `docker compose exec db pg_dump -U cambia cambia > backup.sql` (rotina de backup automatizado ainda não faz parte do sistema — agende esse comando externamente, ex: via `cron`).
- **Restaurar um backup**: `docker compose exec -T db psql -U cambia cambia < backup.sql` (com o sistema parado ou o banco vazio).

## Testar o envio de e-mail antes de configurar o SMTP real (opcional)

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

## Limitações conhecidas

- **Certificado HTTPS autoassinado por padrão.** O `reverse-proxy` gera um certificado sozinho (ver `CERT_CN` acima) — funciona (o tráfego é criptografado de verdade), mas o navegador mostra um aviso de "conexão não segura" na primeira visita, já que não é emitido por uma autoridade confiável. Para eliminar o aviso, é preciso um certificado de uma CA de verdade (interna da empresa, ou pública se houver domínio) — consulte quem entregou o sistema.
- **Senha do Postgres**: definida por você em `POSTGRES_PASSWORD` — **use uma senha forte**, a porta `5432` fica publicada no host por padrão.
- **Envio real de e-mail (SMTP)** depende de credenciais próprias da sua empresa (ver seção acima) — sem isso, login exige acesso aos logs do backend para pegar o token manualmente.
- Sem rotina automatizada de backup do banco — use o comando manual de `pg_dump` citado acima, agendado externamente.

## Suporte

Em caso de dúvida na instalação, erro inesperado, ou para solicitar credenciais/uma nova versão, entre em contato com quem entregou o sistema.

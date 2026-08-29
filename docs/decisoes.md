# Decisões

Decisões já fechadas com o usuário (PO/PM do projeto). Não revisitar sem necessidade.

## Metodologia
- Desenvolvimento incremental, guiado por TDD.
- Cada incremento deve ser pequeno, testável isoladamente e utilizável pelo usuário antes de avançar para o próximo.
- Nenhuma regra de negócio ou fórmula financeira deve ser suposta — dúvidas de negócio são levantadas antes da implementação.

## Stack técnica
- **Backend**: Java 21 / Spring Boot 4
- **Banco de dados**: PostgreSQL
- **Hospedagem**: servidor próprio (on-premise)
- **Migrações de schema**: Flyway — cada entidade nova ganha um script versionado em `src/main/resources/db/migration`. Hibernate roda em modo `validate` (nunca gera/altera schema sozinho).
- **Ambiente de desenvolvimento/teste**: PostgreSQL real via Testcontainers, tanto nos testes quanto ao rodar a aplicação localmente (`mvnw spring-boot:test-run`) — não é necessário Postgres instalado na máquina, só Docker rodando.
- **Estrutura de pacotes**: por funcionalidade/domínio (ex: `com.cambia.cliente`), não por camada técnica.
- **API**: REST simples, DTOs (Java records) separados da entidade JPA para request/response.

## Perfis de acesso
- **Três perfis** (renomeado/ampliado — ver "Edição de Operação, auditoria e perfil Consultor" abaixo): **Admin** (gerencia usuários, clientes, bancos, modelos de cálculo), **Analista** (renomeado de "Usuário" — opera o dia a dia: registra, edita, completa operações) e **Consultor** (só visualização, em todo o sistema).

## Autenticação (Incremento 4)
- **Usuário do sistema tem só Nome + E-mail** (sem senha) — decisão explícita do usuário.
- **Login sem senha, por link mágico**: o usuário informa o e-mail (`POST /auth/magic-link`), recebe um link de uso único válido por 15 minutos, e ao confirmá-lo (`GET /auth/verify`) recebe um token de sessão opaco válido por 8 horas.
- Sessão é enviada pelo cliente em requisições futuras via header `Authorization: Bearer <sessionToken>`.
- **Envio real de e-mail (SMTP) ainda não configurado** — o link é apenas logado (`LoggingMagicLinkSender`). Precisa ser resolvido com a configuração do servidor de e-mail da empresa antes de produção — ver [pendencias.md](pendencias.md).
- **Bootstrap do primeiro Admin**: `POST /auth/bootstrap-admin` cria o primeiro usuário (perfil Admin) apenas se o banco ainda não tiver nenhum usuário; depois disso, sempre retorna `409`. Necessário porque `/usuarios` já exige perfil Admin para cadastrar qualquer usuário.
- Endpoints `/clientes/**`, `/bancos/**` e `/usuarios/**` exigem perfil Admin autenticado. `/auth/**` e `/actuator/health` são públicos.
- Duração dos tokens (15 min / 8h) é um parâmetro técnico, não uma regra de negócio — pode ser revisto se necessário.

## Identificação da Operação
- Além do "código do banco" (texto livre, vindo do banco, quando existir), o sistema terá um **ID do trade** próprio, gerado automaticamente, no formato `AAAA-NNNNNN` (ano + sequencial), similar ao padrão de numeração usado em operações de Comex.
- Esse ID é independente do código que o banco eventualmente fornece.

## Cálculo da Operação
- R$, Total Bruto do Câmbio e Comissão Líquida serão **calculados automaticamente pelo sistema, em tempo real**, a partir de Valor em ME, Nivelamento, Taxa Final e da fórmula de comissão cadastrada no Banco — não digitados manualmente como na planilha atual.
- ~~O percentual de comissão de cada banco é um valor simples~~ — **corrigido no Incremento 11**: é uma fórmula (estilo planilha), não um percentual simples. Ver decisão de "Fórmula de comissão do Banco" abaixo.

## Fórmula de comissão do Banco (Incremento 11)
- Corrige a decisão anterior (que supunha um percentual simples). Cada banco define sua própria **fórmula**, no estilo de fórmula de planilha (ex: `N*70%-N*70%*4,65%`, onde `N` = Total Bruto do Câmbio).
- Única variável suportada por enquanto: `N` (Total Bruto do Câmbio) — confirmado com o usuário. Suporta `+ - * /`, `%`, parênteses, decimais com vírgula ou ponto.
- Implementado com um parser/avaliador próprio (`FormulaComissao`), sem `eval`/scripting, por segurança.
- Entrega em duas etapas, como combinado: **etapa 1 (concluída)** — campo de texto para digitar a fórmula, backend valida e calcula de verdade. **Etapa 2 (concluída no Incremento 12)** — construtor visual de arrastar/soltar, com teste ao vivo da fórmula antes de salvar.
- Fórmula inválida (erro de sintaxe) é rejeitada no cadastro/edição do banco com `400`.
- **Superada em parte pelo Incremento 15** (ver abaixo): a fórmula deixou de ser um campo direto do Banco e virou um cadastro próprio ("Modelo de Cálculo"), reutilizável entre bancos. O parser/avaliador (`FormulaComissao`) e as regras acima continuam valendo, só mudou onde a fórmula é cadastrada.

## Novos campos do Banco e Modelo de Cálculo como cadastro próprio (Incremento 15)
- Pedido direto do usuário (PO): Banco passou a ter **Código do Banco**, **Sigla**, **Nome** e **Taxa de rebate**, além do cálculo de comissão.
- **Taxa de rebate** é só um campo manual armazenado — não participa de nenhuma fórmula/cálculo confirmado até agora (mesmo tratamento dado a Spot Asset na Operação: guardado, não usado, até haver confirmação de negócio).
- A fórmula de comissão (antes um campo de texto direto no cadastro do Banco, Incremento 11) virou um **cadastro próprio, reutilizável**: "Modelo de Cálculo" (`nome` + `formula`), com seu próprio CRUD (`/calculos`) e sua própria tela ("Modelos de Cálculo", em Cadastros). O Banco agora só **seleciona** um modelo já cadastrado (`calculoId`) em vez de digitar a fórmula toda vez — pedido explícito do usuário ("o banco deve apenas selecionar os existentes").
- O parser/avaliador de fórmula (`FormulaComissao`) e o construtor visual de arrastar/soltar (`FormulaBuilder.jsx`) foram reaproveitados sem alteração — só migraram de dono (de Banco para Calculo).
- Um Modelo de Cálculo referenciado por pelo menos um Banco não pode ser removido (`409` — checado explicitamente via `existsByCalculoId`, não por exceção de FK do banco de dados).
- Migração: bancos existentes tiveram sua fórmula antiga migrada automaticamente para um Modelo de Cálculo criado a partir dela (`V13`/`V14`); os novos campos (`codigo_banco`, `sigla`, `taxa_rebate`) receberam valores de placeholder (`N/D`, sigla derivada do nome, `0`) nas linhas pré-existentes, a serem corrigidos manualmente pelo Admin.

## Taxa de Rebate na fórmula de comissão (Incremento 16)
- Pedido do usuário: a Taxa de Rebate do Banco (Incremento 15) precisa participar do cálculo da comissão, junto com o Total Bruto do Câmbio.
- `FormulaComissao` (parser/avaliador) passou a suportar uma segunda variável: `R`, representando a Taxa de Rebate do Banco que está usando aquele Modelo de Cálculo — além de `N` (Total Bruto do Câmbio), inalterado desde o Incremento 11. `R` aceita o mesmo sufixo `%` já suportado por `N` (não foi criada uma regra nova, só estendida a existente para a nova variável).
- Como o Modelo de Cálculo é compartilhável entre bancos (Incremento 15), a mesma fórmula com `R` produz resultados diferentes por banco, cada um com sua própria Taxa de Rebate — é essa a razão de a taxa ficar no Banco e não no Modelo de Cálculo.
- `FormulaBuilder.jsx` ganhou um novo bloco arrastável "R (Taxa Rebate)" e um segundo campo de valor de exemplo ("Taxa de Rebate de exemplo") na área de teste da fórmula, ao lado do "Total Bruto de exemplo" já existente.

## Excel do Fechamento mais informativo (Incremento 19)
Pedido do usuário: o Excel exportado do Fechamento Diário precisa ter linhas e colunas informativas, que ajudem quem abre a planilha a entender os dados sem precisar do sistema. O export em PDF já tinha cabeçalho de coluna nas quebras; o Excel não tinha.
- **Cabeçalho de coluna em toda tabela** — as quebras (Por banco/cliente/moeda/tipo) ganharam a linha "Banco/Cliente/Moeda/Tipo | Quantidade | Total R$ | Comissão Líquida (R$)", que antes não existia (só valores, sem dizer o que cada coluna significava).
- **Rótulos por extenso**: status (`EM_ANDAMENTO`/`COMPLETO`) e C/V (`C`/`V`) passaram a aparecer como "Em andamento"/"Completo" e "C (Compra)"/"V (Venda)" — só na exportação, não muda a API nem o banco.
- **Números com formato de moeda** (`#,##0.00`), não texto puro — abre no Excel já alinhado à direita e pronto para somar/gerar gráfico.
- **"Volume por moeda" e "Exposição por moeda"** viraram mini-tabelas com cabeçalho (Moeda | Volume, Moeda | Exposição) em vez de uma linha de texto solta por moeda.
- Título da planilha mesclado, com uma linha "Gerado em dd/mm/aaaa às HH:mm" logo abaixo — informa quando aquele snapshot foi tirado (o fechamento é sempre recalculado ao vivo, então isso importa para quem salvar o arquivo).
- Bordas finas em todas as células de tabela e fundo diferenciado nos cabeçalhos, pelo mesmo motivo: ajudar a olho nu a separar seção de dado.
- PDF não foi alterado (não foi pedido) — só o Excel.

## Comparar dois períodos à escolha no comparativo (Incremento 20)
Pedido do usuário: na tela de Comparativos e Tendências, poder comparar dois períodos quaisquer escolhidos por ele — não só "período atual x anterior" pré-definido. Exemplo dado: comparar "2 meses atrás" com "mês passado" (dois períodos não-adjacentes ao período corrente). Supera a decisão anterior do Incremento 14a (só períodos pré-definidos) e uma primeira tentativa dentro deste mesmo incremento (intervalo único + "período anterior" calculado automaticamente), que não era o que o usuário queria — ele quer escolher **os dois lados** da comparação.
- `GET /relatorios/comparativo` aceita `inicioA`+`fimA`+`inicioB`+`fimB` como alternativa a `periodo` (o `periodo` original continua existindo e funcionando exatamente como antes, pro caso rápido de "período atual x anterior automático"). Os quatro parâmetros de data são obrigatórios juntos; qualquer `fim` antes do respectivo `início` retorna `400`.
- A resposta (`ComparativoResponse`) não mudou de formato — Período A ocupa os campos "principais", Período B ocupa os campos que antes eram "período anterior" (reaproveita a mesma estrutura, sem precisar de um DTO novo).
- Frontend: modo "Comparar dois períodos à escolha", com um seletor independente para Período A e Período B. Cada lado tem atalhos (Este mês, Mês passado, 2 meses atrás, 3 meses atrás, Este ano, Ano passado) calculados no próprio front, além de "Personalizado" para datas livres. Os rótulos da tela viram "Período A"/"Período B" nesse modo (em vez de "atual"/"anterior", que não fazem sentido pra dois períodos arbitrários do passado).
- O modo rápido original (período pré-definido x anterior automático) continua existindo, sem mudanças — é só uma alternativa mais rápida ao lado da nova.

## Datas sempre em dd/mm/aaaa (Incremento 21)
Pedido do usuário: em todo o sistema, datas exibidas na tela devem aparecer no formato dia/mês/ano — várias telas mostravam a data crua no formato da API (`aaaa-mm-dd`), inconsistente com o resto da interface.
- Novo utilitário `frontend/src/utils/data.js`: `formatarData(iso)` converte `"aaaa-mm-dd"` para `"dd/mm/aaaa"` por manipulação de string (não usa `Date`/fuso horário, pra não arriscar deslocar o dia); `formatarDataHora(iso)` (já existia duplicado em duas telas, centralizado aqui) formata data+hora via `toLocaleString("pt-BR")` para timestamps reais (que têm componente de hora, então não têm o mesmo risco de fuso).
- Aplicado em todas as tabelas e textos que mostravam data crua: Operações, Relatório de Operações, Posição em Aberto, Rankings, Comparativos (todas as datas, inclusive dentro do seletor de dois períodos), Histórico de Fechamentos.
- **Não alterado de propósito**: o valor de `<input type="date">` (exige `aaaa-mm-dd` do próprio HTML), o nome de arquivos baixados (não pode ter `/`), e o cabeçalho do Painel (já usa formato por extenso tipo "Quinta-feira, 27 de agosto de 2026", que não é o formato cru que motivou o pedido).

## Volume de transações no comparativo (Incremento 22)
Pedido do usuário: mostrar o volume de transações na tela de Comparativos e Tendências — confirmado que "volume" cobre tanto a quantidade de operações (contagem) quanto o volume financeiro (soma em R$), nos dois lados da comparação (Período A e B / atual e anterior).
- `ResultadoFinanceiro` (usado também pelo Fechamento Diário) ganhou `quantidadeOperacoes` — contagem de operações completas do dia, ao lado dos totais que já existiam. Aditivo, não quebra nada que já consumia esse DTO.
- `ComparativoResponse.totalOperacoes` (período principal) e `PeriodoComparado.totalOperacoes` (período comparado) somam essa contagem ao longo de todos os dias do intervalo.
- Frontend: cada cartão de período (principal e comparado, nos dois modos — rápido e dois períodos) agora mostra "Quantidade de operações" e "Total R$ (volume financeiro)" lado a lado — antes o cartão do período comparado nem mostrava o Total R$, só a comissão.

## Volume financeiro por moeda no comparativo (Incremento 23)
Pedido do usuário: quebrar o volume financeiro por moeda (das moedas usadas em cada período), não só o total agregado em R$.
- Reaproveitado o mesmo campo `volumePorMoeda` que o Fechamento Diário já calculava por dia (soma do Valor em ME das operações completas, agrupado por moeda — **não é R$, é o valor na própria moeda estrangeira**). Somado ao longo de todos os dias do período em `RelatorioComparativoService`.
- `ComparativoResponse.volumePorMoeda` (período principal) e `PeriodoComparado.volumePorMoeda` (período comparado) — cada um só com as moedas que tiveram operação completa naquele período específico (moedas diferentes podem aparecer em cada lado).
- **Bug corrigido de passagem**: o Excel do Fechamento (Incremento 19) rotulava essa mesma tabela como "Volume por moeda (R$)" — errado, o valor é em ME (moeda estrangeira), não R$. Corrigido para "Volume por moeda (ME)", consistente com o rótulo que a tela de Fechamento já usava corretamente.
- Frontend: tabela "Moeda | Volume (ME)" dentro de cada cartão de período (principal e comparado), nos dois modos.

## Comparar dois períodos também nos Rankings (Incremento 24)
Pedido do usuário: levar a mesma capacidade de comparar dois períodos à escolha (Incremento 20) para a tela de Rankings por Dimensão.
- `GET /relatorios/rankings` aceita `inicio`+`fim` como alternativa a `periodo` (mesmo padrão do comparativo: os três continuam existindo, nenhum comportamento anterior muda). `RankingsResponse` não ganhou campo novo — cada chamada já é autossuficiente (não precisa de um "período anterior" embutido como o comparativo).
- Diferença de design em relação ao comparativo: aqui o **frontend** busca os rankings de cada período com duas chamadas independentes (`Promise.all`) em vez de um endpoint combinado — like o Rankings não calcula variação/comparação entre os dois lados, só exibe as mesmas 6 tabelas duas vezes lado a lado ("Rankings — Período A" / "Rankings — Período B"), não havia necessidade de um DTO combinado no backend.
- **Refatoração**: a lógica de presets de período (Este mês/Mês passado/2 meses atrás/.../Personalizado) e o componente de seleção, que só existiam dentro de `RelatorioComparativoPage.jsx`, foram extraídos para `frontend/src/utils/periodos.js` e `frontend/src/components/SeletorPeriodo.jsx` — reaproveitados por Comparativos e por Rankings, evitando duplicar a mesma lógica de datas duas vezes.

## Mailpit para testar o envio real de e-mail sem o Azure (Incremento 25)
Pedido do usuário: a empresa vai usar o relay SMTP do Microsoft 365/Azure (ver pendência #10), mas o plano ainda não foi contratado — sem credenciais reais, não dava pra testar o caminho de envio de verdade (`SmtpMagicLinkSender`), só o modo de log (`LoggingMagicLinkSender`).
- Adicionado serviço `mailpit` ao `docker-compose.yml` (imagem `axllent/mailpit`) — um servidor SMTP de teste que aceita e-mail sem autenticação e mostra tudo numa interface web (`http://localhost:8025`), sem entregar nada de verdade. Fica sempre disponível junto com os outros serviços; não precisa de nenhum plano/credencial.
- `spring.mail.properties.mail.smtp.auth` e `...starttls.enable` — antes fixos em `true` (necessário pro relay do Microsoft 365) — viraram configuráveis via `SPRING_MAIL_SMTP_AUTH`/`SPRING_MAIL_SMTP_STARTTLS` (default `true`, preservando o comportamento de produção). O Mailpit não usa autenticação nem STARTTLS, por isso precisa dos dois como `false`.
- `.env.example` documenta as duas opções lado a lado: a de produção (Azure, comentada, pra quando o plano existir) e a do Mailpit (pronta pra descomentar e usar já).
- Testado de ponta a ponta: habilitei o envio real (`CAMBIA_MAIL_HABILITADO=true`) apontando pro Mailpit, disparei um link mágico de verdade, confirmei que chegou no Mailpit com o assunto/corpo corretos, e usei o token de dentro do e-mail pra logar normalmente — confirma que todo o caminho `SmtpMagicLinkSender` funciona; só falta trocar host/porta/credenciais quando o plano do Azure existir.

## Tela de Operações dividida em duas (Incremento 27)
Pedido do usuário: a tela "Operações" deve servir só para confirmar, editar e registrar novas ordens (o usuário chama Operação de "ordem", mesmo conceito). Ordens já confirmadas saem dessa tela e passam a viver numa tela nova, "Listagem de Ordens".
- **Operações** (`/operacoes`): continua com o formulário de registro/edição, mas a tabela abaixo agora só lista operações **Em andamento** — são as únicas em que faz sentido editar ou completar ali. Colunas que só existem depois de completa (R$, Total Bruto, Comissão, Completado por) saíram dessa tabela por ficarem sempre vazias.
- **Listagem de Ordens** (`/operacoes/listagem`, tela nova): lista só as operações **Completas**, com todas as colunas calculadas — sem formulário, sem ações (editar/completar uma operação completa já é bloqueado pela regra existente).
- Nenhuma mudança de backend — as duas telas usam o mesmo `GET /operacoes` e filtram por status no front. Permissão de acesso não mudou: todo perfil autenticado (incluindo Consultor) continua vendo as duas telas, só sem formulário/ações pra quem não pode escrever (mesma regra de antes, agora šplit em duas telas).
- Corrigido de passagem: o item "Operações" do menu lateral usava match por prefixo de URL, então ficava marcado como ativo também dentro de "/operacoes/historico" (e agora "/operacoes/listagem"). Virou match exato, só os três itens da família Operações destacam independentemente.

## Envio automático do fechamento por e-mail (Incremento 26)
Pedido do usuário: enviar o PDF e o Excel do fechamento diário por e-mail, automaticamente todos os dias, para uma lista de destinatários cadastrada na tela de Fechamento.
- **Origem dos destinatários**: confirmado com o usuário — são selecionados entre os Usuários já cadastrados no sistema (não é um cadastro de e-mail livre/independente). Tabela nova `fechamento_destinatarios` guarda só o `usuario_id` de quem deve receber; o Admin marca/desmarca via checklist na tela de Histórico de Fechamentos.
- **Anexos**: confirmado que o PDF e o Excel vão juntos, anexados na mesma mensagem (um e-mail por dia por destinatário, não um e-mail por arquivo).
- `FechamentoAgendadorService` (o job que já gerava e guardava o PDF/Excel no horário configurado) passou a, logo depois de gerar, também enviar o e-mail para cada destinatário cadastrado — só entra em ação se `cambia.mail.habilitado=true` (mesma flag do link mágico, ver Mailpit/Incremento 25) e se houver pelo menos um destinatário; caso contrário não faz nada, sem gerar erro.
- `FechamentoEmailService` (novo, `@ConditionalOnProperty` igual ao `SmtpMagicLinkSender`) monta um e-mail MIME com os dois anexos por destinatário. Falha ao enviar para UM destinatário não interrompe o envio pros outros (cada envio é isolado, erro só vira log).
- `GET/PUT /fechamentos/destinatarios`: GET aberto a Admin e Analista (mesma regra do resto de `/fechamentos/**`); PUT só Admin (mesma regra do horário de geração) — substitui a lista inteira a cada chamada, não soma.
- Testado de ponta a ponta com o Mailpit (Incremento 25): configurei um destinatário, forcei o horário de geração pro minuto seguinte, e confirmei que o e-mail chegou com os dois anexos (PDF e XLSX) de verdade, com o conteúdo correto.

## Filtros na Listagem de Ordens (Incremento 28)
Pedido do usuário: adicionar filtros na tela "Listagem de Ordens" (Incremento 27) — Data de fechamento, Moeda, Valor Moeda, CNPJ, Nome.
- **"Data de fechamento" interpretado como `completadoEm`** (quando a ordem foi confirmada), não a "Data" já existente na tabela (data da operação em si) — os dois nomes distintos usados pelo usuário indicam campos diferentes. Coluna "Completado em" foi adicionada à tabela para o efeito do filtro ficar visível.
- **Filtros de texto (Moeda, Valor Moeda, CNPJ, Nome)** usam contém/case-insensitive, não igualdade exata; **Data de fechamento** usa igualdade exata (um `<input type="date">`, comparado no fuso do navegador). Escolha de UX, não regra de negócio.
- Todos os filtros rodam **no front-end**, sobre a lista já carregada por `GET /operacoes` — sem novo endpoint nem parâmetro de query no backend.
- **Backend**: `OperacaoResponse` ganhou `clienteDocumento` (CNPJ), embutido diretamente na resposta pelo mesmo motivo do Incremento 17 (`clienteNome`/`bancoNome`) — Consultor não pode chamar `/clientes`, então o documento do cliente precisa vir junto na própria operação para o filtro de CNPJ funcionar nessa tela também para esse perfil. Nova coluna "CNPJ" na tabela, ao lado de "Cliente".
- Botão "Limpar filtros" só aparece quando algum filtro está ativo.
- Testado ao vivo (Playwright): filtro por Nome isola corretamente as ordens do cliente buscado; filtro por Moeda isola só as ordens daquela moeda; filtro por Data de fechamento sem correspondência mostra o estado vazio "Nenhuma ordem encontrada".

## "Operações"/"Operação" trocado por "Ordens" na interface (Incremento 29)
Pedido do usuário: trocar todas as palavras "Operações"/"Operação" por "Ordens" no sistema. Esclarecido com o usuário que o alcance é **só os textos visíveis na tela** (títulos, rótulos, botões, mensagens) — não as URLs, nem o código (classes Java, endpoints da API, tabelas do banco), que continuam usando "Operação"/`/operacoes` internamente.
- Nenhuma mudança de comportamento, só texto: menu lateral ("Ordens", "Listagem de Ordens" — já usava esse nome desde o Incremento 27 —, "Histórico de Ordens", "Relatório de ordens"), títulos de tela, rótulos de card/tabela, mensagens de estado vazio e o texto de confirmação ao completar uma ordem.
- **Não alterado de propósito**: rotas do front-end (`/operacoes`, `/operacoes/listagem`, `/operacoes/historico`, `/relatorios/operacoes`), a palavra "Operacional" (nome da seção do menu e do bloco "Resumo operacional" do Fechamento — é uma palavra diferente, não "Operação"/"Operações"), e todo o código (variáveis, endpoints, nomes de tabela) — conforme escopo escolhido pelo usuário.
- Testado ao vivo (Playwright, perfil Admin): Painel, Ordens, Listagem de Ordens, Histórico de Ordens, Relatório de Ordens e Posição em Aberto — todos exibindo "Ordens" onde antes era "Operações", sem erro de console.

## Filtros de pesquisa na tela de Ordens (Incremento 30)
Pedido do usuário: adicionar filtros de pesquisa também na tela de Ordens (a antiga "Operações"), para facilitar achar uma ordem específica na hora de completá-la.
- Mesmos 5 campos e a mesma lógica de filtro já usados na Listagem de Ordens (Incremento 28): Data, Moeda, Valor Moeda, CNPJ e Nome — textos por contém/case-insensitive, Data por igualdade exata. Única diferença: aqui o campo chama-se só "Data" (não "Data de fechamento"), porque ordens "Em andamento" ainda não têm data de conclusão — filtra pela própria data da ordem (`op.data`), o mesmo campo já exibido na coluna "Data" da tabela.
- Nova coluna "CNPJ" na tabela (entre "Cliente" e "Banco"), usando o mesmo `clienteDocumento` já embutido em `OperacaoResponse` desde o Incremento 28 — nenhuma mudança de backend necessária.
- Filtros ficam entre o formulário de registro/edição e a tabela; "Limpar filtros" só aparece com algum filtro ativo. Um novo estado vazio ("Nenhuma ordem encontrada") aparece quando existem ordens em andamento mas nenhuma bate com o filtro — distinto do estado vazio original ("Nenhuma ordem em andamento"), que continua valendo quando não há nenhuma ordem em andamento de fato.
- Testado ao vivo (Playwright, perfil Admin): filtro por Nome isola a ordem certa, filtro por Moeda isola as ordens daquela moeda, e uma Data sem correspondência mostra o estado vazio de filtro.

## Menu lateral reduzido com abas dentro da página (Incremento 31)
Pedido do usuário: reduzir o número de itens do menu lateral, achando muitas opções (14 itens pro Admin/Analista). Apresentadas 3 abordagens com trade-offs diferentes (abas dentro da página, grupos recolhíveis, busca rápida estilo Ctrl+K) — usuário escolheu **abas dentro da página**.
- Telas que antes eram itens separados do menu viraram **abas de uma página só**, sem remover nenhuma tela nem funcionalidade — só reorganiza a navegação:
  - **Ordens** (era "Operações" + "Listagem de Ordens" + "Histórico de Ordens", 3 itens): agora 1 item "Ordens" com abas "Em andamento" / "Confirmadas" / "Histórico".
  - **Fechamento** (era "Fechamento" + "Histórico de fechamentos", em grupos diferentes): agora 1 item "Fechamento" com abas "Diário" / "Histórico".
  - **Relatórios** (eram 4 itens: Relatório de ordens, Comparativos, Rankings, Posição em aberto): agora 1 item "Relatórios" com 4 abas.
  - **Cadastros** (eram 4 itens: Clientes, Bancos, Modelos de Cálculo, Usuários): agora 1 item "Cadastros" com 4 abas — a aba "Usuários" só aparece pro perfil Admin (mesma regra de acesso de antes, só mudou de item de menu pra aba).
  - Resultado: **14 → 5 itens** no menu do Admin/Analista (Painel, Ordens, Fechamento, Relatórios, Cadastros); **3 → 1 item** pro Consultor (só "Ordens", que já era tudo que ele via).
- Implementado com um componente novo `components/SubNav.jsx` (barra de abas, reaproveitado nas 4 páginas agrupadas) — cada aba é uma rota de verdade (não é só um `useState` trocando conteúdo), então URL, F5 e "voltar" do navegador continuam funcionando normalmente.
- `/relatorios` e `/cadastros` (sem sub-rota) redirecionam pra primeira aba (`/relatorios/operacoes` e `/cadastros/clientes`) — são as URLs que os itens do menu apontam.
- **Única mudança de URL**: as telas de Cadastros migraram de `/clientes`, `/bancos`, `/calculos`, `/usuarios` para `/cadastros/clientes`, `/cadastros/bancos`, `/cadastros/calculos`, `/cadastros/usuarios` (precisava de um prefixo comum pra funcionar como abas de uma página). Nenhuma outra URL mudou. Sem impacto no backend/API — são só rotas do front-end.
- Testado ao vivo (Playwright) nos 3 perfis: Admin e Analista com o menu de 5 itens e todas as abas navegáveis e destacando a aba ativa corretamente; Consultor com o menu de 1 item só.

## Nivelamento e Spot Asset com até 4 casas decimais (Incremento 32)
Pedido do usuário: os campos Nivelamento e Spot Asset da Operação podem aceitar até 4 casas decimais.
- O front-end (`input type="number" step="0.0001"`) já usava esse step desde a implementação original — o navegador já impedia submeter o formulário com mais de 4 casas decimais nesses dois campos (validação nativa HTML5).
- **Reforçado no backend**, que até então não validava isso (só a UI, facilmente contornável chamando a API direto): `OperacaoRequest.spotAsset`/`nivelamento` ganharam `@Digits(integer = 8, fraction = 4)`, rejeitando com `400` qualquer valor com mais de 4 casas decimais, também para clientes da API que não passam pelo formulário. `integer = 8` é só folga técnica (a coluna do banco é `NUMERIC(12,6)`, sem mudança de schema) — a regra de negócio em si é só a fração.
- Taxa Final e Valor em ME não foram alterados — o pedido foi só para Nivelamento e Spot Asset.
- Testado: 3 testes novos em `OperacaoControllerTests` (rejeita Spot Asset e Nivelamento com 5 casas decimais, aceita com exatamente 4) e verificação ao vivo no navegador confirmando que 4 casas decimais são aceitas (ordem criada de ponta a ponta) e que 5 casas decimais são bloqueadas na validação nativa do campo antes mesmo de enviar ao backend.

## Data da ordem não pode ser editada (Incremento 33)
Pedido do usuário: a Data de uma ordem não pode ser alterada depois de criada — trocar a data de uma operação exige criar uma nova ordem, não editar a existente.
- **Backend**: `Operacao.editar(...)` deixou de aceitar `data` como parâmetro — a data é ignorada em qualquer `PUT /operacoes/{id}`, mesmo que o cliente da API envie um valor diferente do original (protege a regra mesmo fora do formulário, chamando a API direto). `data` continua sendo obrigatória e usada normalmente na criação (`POST /operacoes`).
- **Frontend**: o campo Data do formulário fica desabilitado (`disabled`) quando a tela está em modo de edição (com uma dica ao passar o mouse explicando o motivo), e volta a ficar editável ao cancelar a edição ou ao começar uma nova ordem.
- Atualiza a decisão anterior ("Edição de Operação, auditoria e perfil Consultor") — Data passa a ser a única exceção entre os campos manuais editáveis.
- Testado: teste novo em `OperacaoEdicaoTests` confirmando que uma tentativa de mudar a data via `PUT` é silenciosamente ignorada (a data original é mantida na resposta e numa consulta seguinte); suite completa (142 testes) sem regressão. Verificado ao vivo no navegador: campo habilitado ao criar, desabilitado (com a data original preenchida) ao editar, habilitado de novo ao cancelar.

## Bug: erro de validação deslogava o usuário em vez de mostrar o que houve (Incremento 34)
Pedido do usuário: testou registrar uma ordem com Nivelamento/Spot Asset de 5 casas decimais (rejeitado pelo Incremento 32) e, em vez de ver um erro explicando o motivo, o sistema o deslogava toda vez. Pediu que **todo erro tenha um pop-up reportando o que houve**.

**Causa raiz encontrada** (não era sobre Nivelamento/Spot Asset especificamente — era um bug bem mais amplo e sério): no servidor real (fora dos testes MockMvc, que não reproduzem esse cenário), **qualquer erro de negócio** — falha de validação (`@Valid`), um `404` de "operação não encontrada", ou até uma URL que não existe — era convertido em **`401` com corpo vazio**, e o front-end trata todo `401` como sessão expirada, apagando o login e mandando pra tela de login. Ou seja: qualquer erro de validação (não só decimais) parecia um logout aleatório, sem explicação nenhuma.

Diagnosticado passo a passo, eliminando primeiro falsas pistas (token trocado entre chamadas, corrida com o Mailpit nos testes) até isolar o padrão real com curl puro, sem navegador: **toda resposta de erro do Spring (validação, 404 manual, rota inexistente) passa por um redirecionamento interno pro caminho `/error`** (`BasicErrorController`, mecanismo padrão do Spring Boot pra montar o corpo do erro) — e esse `/error` não estava liberado na configuração de segurança, então caía em "não autenticado" e virava `401`, mascarando o status e a mensagem originais (400, 404, o que fosse).

- **Corrigido**: `/error` liberado (`permitAll()`) na configuração de segurança — deixa o Spring montar o corpo do erro original sem exigir autenticação de novo nesse redirecionamento interno.
- **Melhorado junto**: `spring.mvc.problemdetails.enabled=true` (formato padrão RFC 7807) e um novo `TratamentoErroGlobal` (`@ControllerAdvice`, primeiro do projeto) que monta a mensagem de erro de validação como "campo: motivo" (ex: `"nivelamento: numeric value out of bounds (<8 digits>.<4 digits> expected)"`), em vez do genérico "Invalid request content." que o Spring devolve por padrão — vale pra qualquer campo de qualquer cadastro do sistema, não só Operação.
- **Front-end**: `api/client.js` passou a ler também o campo `detail` (formato RFC 7807) na composição da mensagem de erro, além dos campos antigos (`message`/`error`) — sem isso a mensagem nova do backend não aparecia no pop-up.
- Um `401` de verdade (token inválido/expirado) continua funcionando exatamente como antes — só passou a acontecer quando é mesmo o caso, não mais pra qualquer erro de negócio.
- Testado: suite completa (142 testes) sem regressão, incluindo um teste novo que verifica o corpo do erro (não só o status); e verificação ao vivo no navegador reproduzindo exatamente o cenário do usuário (Nivelamento/Spot Asset com 5 casas decimais) — confirmando que agora aparece um pop-up com a mensagem real, o usuário permanece logado e os dados do formulário continuam preenchidos pra corrigir e reenviar.

## Refatoração: toda a API com erros claros e completos (Incremento 35)
Pedido do usuário, na sequência do bug do Incremento 34: revisar a API inteira e garantir que **todo** erro tem uma mensagem clara e completa — nada de mensagens genéricas.

Auditoria em `TratamentoErroGlobal` (já criado no Incremento 34) encontrou vários outros pontos ainda genéricos ou mudos, todos corrigidos:

- **Valor de enum inválido** no corpo (ex.: `"status":"CANCELADO"` num campo que só aceita `EM_ANDAMENTO`/`COMPLETO`) — antes "Failed to read request", agora explica o valor rejeitado e as opções válidas.
- **JSON malformado** (sintaxe quebrada no corpo da requisição) — antes "Failed to read request", agora mostra o erro de sintaxe de verdade (posição/caractere inesperado).
- **Path variable com tipo errado** (ex.: `GET /operacoes/abc`, `GET /fechamentos/nao-eh-uma-data`) — antes uma mensagem técnica em bloco só; agora diz claramente qual campo e valor foram rejeitados e o tipo esperado.
- **403 (perfil sem permissão)** e **401 (sessão expirada/inválida)** — antes corpo **vazio** (só o código HTTP, sem explicação nenhuma); agora os dois têm uma mensagem clara, no mesmo formato `detail` usado pelo resto da API. Corrigido de passagem um bug de acentuação (UTF-8) nessas duas mensagens que apareciam corrompidas ("n�o" em vez de "não").
- **Remover um registro em uso** (ex.: Usuário que já logou, Cliente/Banco com Operação vinculada) — antes um `500 Internal Server Error` completamente genérico (violação de chave estrangeira do banco, sem tratamento); agora um `409 Conflict` explicando que o registro está em uso. Resolve de vez a pendência conhecida desde o Incremento 17 ("remover um Usuário que já criou operações falha sem erro claro").
- **Qualquer erro inesperado não previsto** (um bug de verdade) — agora tem um último handler que devolve uma mensagem de alto nível (sem vazar stack trace) em vez do "Internal Server Error" mudo; a stack trace completa continua indo pro log do servidor normalmente, só não pro cliente.
- Front-end (`api/client.js`): a função de baixar arquivo (PDF/Excel do fechamento) ganhou o mesmo tratamento de mensagem de erro que o resto da API já tinha — antes mostrava só "Erro 500 ao baixar o arquivo".
- **Detalhe técnico**: `Usuario`/`Cliente`/`Banco` (as três entidades com risco real de violação de chave estrangeira ao remover) passaram a chamar `repository.flush()` logo após o `delete()`, forçando o erro a aparecer na hora — sem isso, em alguns cenários o erro só apareceria num momento posterior e menos previsível.
- **Enum inválido em parâmetro de URL** (ex.: `?periodo=XPTO`) — antes só "esperado Periodo" (nome cru da classe Java); agora lista os valores aceitos: `"esperado um de: HOJE, SEMANA, MES, ANO"`.
- Testado: novo `TratamentoErroGlobalTests` (9 casos, cobrindo cada um dos itens acima) + suite completa (151 testes) sem regressão; verificação ao vivo cobrindo mais de 15 cenários direto na API (todos os controllers da aplicação, um por um: parâmetro obrigatório faltando, corpo vazio, método HTTP não suportado, `Content-Type` errado, horário inválido no fechamento, fórmula de cálculo inválida, destinatário inexistente, etc.) e no navegador (tentativa de remover um Cliente com Ordem vinculada mostrando o pop-up claro, em vez do erro genérico de antes).

## Erros aparecem em pop-up (toast), não mais numa faixa dentro da página (Incremento 36)
Pedido do usuário: confirmar como os erros aparecem no front-end e, se não for num pop-up/modal, implementar. Antes, cada tela mostrava o erro numa faixa vermelha (`ErrorBanner`) embutida no meio do próprio conteúdo da página — não era um pop-up de verdade, e cada uma das ~15 telas precisava lembrar de chamar isso manualmente.

- **Novo mecanismo global**: `api/client.js` (o único ponto por onde toda chamada à API passa) agora dispara um pop-up automaticamente em **qualquer** erro de requisição — sem precisar que cada tela individualmente trate isso. Implementado como um "toast" no canto superior direito da tela (`ErrorToasts.jsx` + `utils/toastBus.js`), empilhável (vários erros ao mesmo tempo viram vários pop-ups, cada um fechável sozinho ou some depois de 8s).
- **Sessão expirada é um caso especial**: como esse erro dispara um redirecionamento de página inteira pro `/login` (que apagaria um pop-up React normal antes de aparecer), a mensagem é guardada no `sessionStorage` e exibida assim que a tela de login carrega — confirmado ao vivo que o pop-up "Sessão expirada ou inválida" aparece corretamente depois do redirecionamento.
- **Removido** o `ErrorBanner` (faixa inline) e o estado `erro`/`setErro` de todas as ~15 telas que usavam esse padrão — o pop-up global cobre o mesmo caso, então manter os dois ao mesmo tempo mostraria a mensagem duas vezes. Único ajuste de lógica (não só remoção): o Painel usava o estado de erro pra decidir se mostra os KPIs ou uma tela de erro cheia — trocado por checar se os dados vieram nulos, com uma mensagem genérica de fallback (já que o motivo específico já apareceu no pop-up).
- Testado ao vivo: erro de validação (dois seguidos, empilhando dois pop-ups), erro 401 sobrevivendo ao redirecionamento pro login, e uma varredura sem erros de console em 14 telas diferentes pra garantir que a remoção do banner não quebrou nada.

## Botão de completar ordem renomeado para "Confirmar" (Incremento 37)
Pedido do usuário: trocar a palavra usada para completar uma ordem por "Confirmar".
- Alterado só o texto visível: botão da tabela ("Ordens" → aba "Em andamento"), título do modal de confirmação e o botão de confirmar dentro dele — os três agora dizem "Confirmar" (antes "Completar"/"Completar ordem").
- **Não alterado de propósito**: nomes internos de variável/função (`operacaoParaCompletar`, `confirmarCompletar`), a frase descritiva dentro do modal ("Confirma completar a ordem..."), e o rótulo de status "Completo"/"Completada" (Listagem de Ordens, Histórico, badges) — são conceitos diferentes (estado da ordem, não o botão de ação).
- Verificado ao vivo no navegador.

## Quantidade de ordens por moeda no Volume por Moeda do Fechamento (Incremento 38)
Pedido do usuário: a tabela "Volume por moeda" do Fechamento Diário (dentro de "Resultado financeiro") deve mostrar também a quantidade de ordens de cada moeda, não só o volume.
- `ResultadoFinanceiro` ganhou um novo campo aditivo `quantidadePorMoeda` (`Map<String, Integer>`), calculado em paralelo ao `volumePorMoeda` já existente (mesma contagem que já existia em "Quebra por moeda", só que aplicada especificamente à tabela de volume em ME).
- Como esse mesmo campo `volumePorMoeda` é reaproveitado pelos Comparativos (Incremento 23), o novo campo foi adicionado sem alterar a assinatura de nada que o Comparativo usa — `RelatorioComparativoService` não precisou de nenhuma mudança, continua lendo só os campos que já usava.
- **Tela**: nova coluna "Quantidade" na tabela "Volume por moeda" do Fechamento Diário.
- **Excel**: mesma coluna "Quantidade" adicionada só nessa tabela específica (a de "Exposição por moeda", na seção de Posição em Aberto, não ganhou essa coluna — não foi pedido, e não é o mesmo conceito: exposição é sobre ordens em andamento acumuladas, não do dia).
- **PDF**: cada linha de volume por moeda passou a incluir a quantidade entre parênteses (ex.: "Volume USD: 1.000,00 (3 ordens)").
- Testado: teste automatizado novo (valida `quantidadePorMoeda.USD`/`.EUR` no JSON do fechamento) + suite completa (151 testes) sem regressão; verificação ao vivo confirmando a coluna na tela e o valor batendo com a resposta da API.

## Status da Ordem: só ANDAMENTO / CONFIRMADO / CANCELADO (Incremento 39)
Pedido do usuário: a Ordem passa a ter só esses três status possíveis (antes eram dois: `EM_ANDAMENTO`/`COMPLETO`). `CANCELADO` é um status novo. Esclarecido com o usuário antes de implementar (afeta cálculo financeiro, zona de "nunca inventar"):

- **De onde se chega em CANCELADO**: só a partir de `ANDAMENTO`. Uma ordem `CONFIRMADO` não pode ser cancelada (é definitivo, mesma regra que já existia pra "completo" antes).
- **Cálculo**: uma ordem `CANCELADO` **não tem** valores calculados (R$, Total Bruto do Câmbio, Comissão Líquida) — mesmo comportamento de uma ordem em andamento, não entra em nenhum total financeiro (Fechamento, Relatórios).
- **Reversibilidade**: nenhuma. Uma vez `CANCELADO`, a ordem trava — não edita, não muda de status de novo (nem de volta pra `ANDAMENTO`, nem pra `CONFIRMADO`).
- **Onde aparece**: `CANCELADO` não ganhou aba própria na tela "Ordens" — só é visível pelo Histórico de Ordens (que já registra todo evento). As abas "Em andamento" e "Confirmadas" continuam mostrando só os dois status originais, agora renomeados.

### Renome dos status existentes
- `EM_ANDAMENTO` → `ANDAMENTO`, `COMPLETO` → `CONFIRMADO`. Migração (`V16`) atualiza os dados já existentes (`operacoes.status` e `operacoes_eventos.tipo`, esse último trocando `COMPLETADA` por `CONFIRMADA`) — nenhuma ordem ou evento de auditoria já registrado perde a informação.
- O evento de auditoria ganhou `CANCELADA` (`TipoEventoOperacao`), registrado no Histórico de Ordens junto com `CRIADA`/`EDITADA`/`CONFIRMADA` já existentes.

## Coluna "Tipo" (PR/CR/VIR) nas listagens de Ordens (Incremento 40)
Pedido do usuário: as telas de listagem de Ordens devem exibir o tipo da ordem (`Pronto`/`Crédito`/`Virtual`). O campo (`prCrVir`) já existia na API desde sempre — só tinha sido removido do **formulário** de registro no Incremento 18, nunca das telas de listagem, que simplesmente nunca chegaram a mostrá-lo.
- Mudança só de front-end (o campo já vinha em `OperacaoResponse.prCrVir`): nova coluna "Tipo" adicionada, entre C/V e Moeda, em `OperacoesPage.jsx` (aba "Em andamento"), `ListagemOrdensPage.jsx` (aba "Confirmadas") e `RelatorioOperacoesPage.jsx` (Relatório de Ordens).
- Confirmado com o usuário: toda ordem cadastrada manualmente pelo formulário é sempre `Pronto` (ver Incremento 18); `Virtual`/`Crédito` só aparecem em ordens antigas que já tinham esse valor.

## Coluna "Valor Absoluto" nas listagens de Ordens (Incremento 41)
Pedido do usuário, com a fórmula já pronta (não é uma suposição): `Valor Absoluto = |Taxa Final − Nivelamento| × Valor em ME`, com 2 casas decimais.
- Essa fórmula é matematicamente o módulo do Total Bruto do Câmbio já existente (que já calcula `Valor em ME × (Nivelamento − Taxa Final)` ou o inverso, dependendo do C/V) — mas calculado **direto a partir de Taxa Final e Nivelamento**, sem depender do C/V. Isso importa no caso de C/V desconhecido (`NA`), onde o Total Bruto do Câmbio fica `null` (nenhuma fórmula confirmada para o spread com direção desconhecida) mas o Valor Absoluto continua calculável normalmente, já que não depende de direção.
- **Regra de quando existe**: segue a mesma regra dos demais valores calculados da operação (R$, Total Bruto, Comissão) — só existe quando a ordem está `CONFIRMADO`; em `ANDAMENTO`/`CANCELADO` vem `null`.
- Implementado em `OperacaoCalculo.calcular()` (novo campo em `ValoresCalculados` e `OperacaoResponse`).
- **Tela**: nova coluna "Valor Absoluto" em `ListagemOrdensPage.jsx` (entre "Total Bruto" e "Comissão") e em `RelatorioOperacoesPage.jsx` (entre "R$" e "Comissão", tela não tinha coluna de Total Bruto). Não aparece em `OperacoesPage.jsx` (aba "Em andamento"), que não mostra nenhum valor calculado — consistente com o padrão já existente pras outras colunas calculadas.
- Testado: `OperacaoCalculoTests` (assertivas novas em todos os cenários existentes, incluindo C/V desconhecido) + `OperacaoControllerTests` (JSON da API) + suite completa sem regressão.

### O que mudou no back-end
- `OperacaoService.atualizarStatus()` ganhou a validação de transição que não existia antes (`PATCH /operacoes/{id}/status` aceitava qualquer status vindo de qualquer status atual, sem checar nada) — agora só permite `ANDAMENTO → CONFIRMADO` ou `ANDAMENTO → CANCELADO`; qualquer outra tentativa (ordem já `CONFIRMADO`/`CANCELADO`, ou pulando pra um status não permitido) devolve `409` com mensagem clara.
- `OperacaoService.toResponse()`: valores calculados só quando `CONFIRMADO` (antes era `!= COMPLETO`, mesma lógica, só o nome mudou — `CANCELADO` cai automaticamente no "sem cálculo" por não ser `CONFIRMADO`).
- `FechamentoService`/`RelatorioPosicaoAbertoService`: contas de "em aberto" e "resultado financeiro" atualizadas pros novos nomes — nenhuma mudança de comportamento pra `ANDAMENTO`/`CONFIRMADO`, `CANCELADO` fica de fora dos dois (não é "em aberto" nem "confirmado").

### O que mudou no front-end
- Tela "Ordens": **sem** aba nova pra Cancelado (conforme decidido) — só um botão "Cancelar" (vermelho, `btn-danger`) ao lado de "Confirmar" na aba "Em andamento", com um modal de confirmação próprio (mensagem deixando claro que não pode ser desfeito) — reaproveita o `ConfirmModal` já existente, que ganhou uma prop `perigo` pra estilizar o botão de confirmar como destrutivo.
- `StatusBadge`, rótulos de filtro (Relatório de Ordens) e o mapa de tipos de evento (Histórico de Ordens) atualizados pros três status/quatro tipos de evento.
- Painel: o card "Ordens hoje" agora soma corretamente confirmadas + em andamento + canceladas (antes fazia `total - completas`, que ficaria errado silenciosamente com um terceiro status possível).
- Textos ajustados de "completo"/"completas" pra "confirmado"/"confirmadas" em todo lugar que se referia ao status (Fechamento, Painel, Relatório de Ordens, Listagem de Ordens).
- Testado: 3 novos testes de backend (cancelar não calcula valor; não permite confirmar/cancelar uma ordem que já saiu de "em andamento"; não permite editar uma ordem cancelada) + suite completa (154 testes) sem regressão; verificação ao vivo — ordem cancelada some da aba "Em andamento", aparece com badge vermelho "Cancelado" no Painel e como evento "Cancelada" no Histórico.

## Relatórios (Incremento 14)
Escopo da v1 confirmado com o usuário (análise completa em [relatorios-analise.md](relatorios-analise.md)):
- **Categorias incluídas**: Operações filtradas, Comparativos e tendências, Rankings por dimensão, Posição/exposição em aberto — as quatro categorias do documento de análise, nenhuma deixada de fora.
- **Permissão**: mesma regra do Fechamento Diário — qualquer usuário autenticado vê os relatórios completos, sem filtro por quem criou (não há painel pessoal restrito para Usuário comum).
- **Formato**: só tela interativa com filtros por enquanto — sem export em PDF/Excel nesta v1 (diferente do Fechamento Diário).
- **Período**: opções pré-definidas (hoje, semana, mês, ano), não seleção livre de datas. ~~Ainda vale para Operações filtradas, Rankings e Posição em aberto~~ — **Comparativos e Tendências ganhou seleção livre de datas no Incremento 20** (ver abaixo), a pedido do usuário.
- Dividido em sub-etapas, seguindo o mesmo padrão do Incremento 13: **14a** Operações filtradas, **14b** Comparativos e tendências, **14c** Rankings por dimensão, **14d** Posição/exposição em aberto.

### 14a — Relatório de Operações filtradas
- **Convenção de período assumida** (não é regra de negócio, é só padrão de UI — passível de ajuste se o usuário esperar outra coisa): "semana" = semana corrente completa (segunda a domingo), "mês" = mês corrente completo (dia 1 ao último dia), "ano" = ano corrente completo (1º de janeiro a 31 de dezembro). Não é "até hoje" (to-date).

## Fechamento Diário (Incremento 13)
Escopo da v1 confirmado com o usuário: Resumo operacional + Resultado financeiro + Quebras por dimensão + Posição em aberto (ver [fechamento-diario-analise.md](fechamento-diario-analise.md)). Comparativos/tendências ficam para o item futuro "Relatórios" (não misturar escopos). O fechamento é sempre recalculado ao vivo (não trava/congela os números do dia). Export em PDF e Excel, com geração automática agendada em horário configurável pelo próprio usuário do sistema (não fixo no código), e uma tela de histórico para baixar exports já gerados.

### 13a — Auditoria na Operação (pré-requisito)
- Operação passou a registrar automaticamente quem criou (`criadoPorUsuarioId`/`criadoEm`) e quem completou (`completadoPorUsuarioId`/`completadoEm`) — capturado do usuário autenticado, não digitado por ninguém.
- Não é um histórico de auditoria completo (não rastreia toda mudança de todo campo) — só os dois eventos pedidos: criação e conclusão.

### 13d — Job agendado e histórico
- Horário de geração fica numa tabela de configuração de uma linha só (`configuracao_fechamento`), editável só por Admin; qualquer usuário autenticado pode consultar o horário atual.
- Job (`@Scheduled`) roda todo minuto e decide se deve gerar com base no horário configurado — a decisão em si (`verificarEGerarSeNecessario`) recebe `LocalDateTime` como parâmetro em vez de usar o relógio do sistema diretamente, para poder ser testada sem depender de tempo real.
- Arquivos gerados automaticamente ficam guardados no banco (`fechamentos_gerados`, com o conteúdo binário em `byte[]`) — não em disco — para sobreviver a reinícios do container sem depender de volume extra.
- **Pegadinha de mapeamento**: `byte[]` anotado com `@Lob` faz o Hibernate 7 mapear para `oid` no Postgres, mas a migração criava a coluna como `bytea` — dava erro de schema na subida. Corrigido removendo `@Lob`: `byte[]` puro já mapeia para `bytea`/`VARBINARY` por padrão.

## Acesso a Cliente/Banco (Incremento 10)
- `GET /clientes` e `GET /bancos` liberados para qualquer usuário autenticado (Admin ou Usuário) — necessário para o Usuário conseguir escolher Cliente/Banco ao registrar uma Operação. `POST/PUT/DELETE` continuam exclusivos do Admin. `/usuarios/**` continua 100% Admin.

## Containerização (Incremento 9)
- `docker-compose.yml` na raiz sobe backend + frontend + PostgreSQL real (não Testcontainers) juntos, com um comando (`docker compose up -d --build`).
- Conexão do backend com o Postgres real via variáveis de ambiente (`SPRING_DATASOURCE_*`), não hardcoded — resolve a pendência de configuração de banco real que estava aberta desde o Incremento 1.
- Credenciais do `docker-compose.yml` (`cambia`/`cambia`) são só para desenvolvimento local — **precisam ser trocadas por segredos de verdade antes de qualquer uso real**.

## Front-end (Incremento 8)
- **React** (Vite), projeto separado em `frontend/`, consumindo a API REST do backend via `fetch`.
- Autenticação no navegador: `sessionToken` em `localStorage`, enviado via header `Authorization: Bearer`.
- Escopo inicial: login + CRUD de Cliente, Banco, Usuário. Tela de Operação fica para quando as pendências relacionadas forem resolvidas.
- CORS liberado no backend para `http://localhost:5173` (dev).

## Redesign visual do front-end
Redesign completo da UI (pedido do usuário: "interface moderna, profissional e consistente"), sem alterar nenhuma lógica de negócio ou chamada de API existente — só apresentação. Processo: análise dos problemas de UI/UX → exploração de 2 direções visuais num canvas de design separado → usuário aprovou um híbrido ("layout do B com a paleta do A") → implementação no código real.
- **Design tokens** em `index.css` (`--bg`, `--surface`, `--accent` índigo `#4f46e5`, `--success`, `--danger`, tokens de sidebar, radius, sombras). Fontes via Google Fonts: **Inter** (interface) e **IBM Plex Mono** (números — trades, valores monetários, todas as métricas em destaque).
- **Navegação**: nav superior trocada por **sidebar fixa** agrupada em três seções (Operacional, Relatórios, Cadastros — a última só para Admin), com item ativo destacado. Resolve o problema real de 8+ links de nav quebrando em duas linhas.
- **Painel** (`PainelPage.jsx`) substitui a home vazia ("Bem-vindo, Admin!") por um dashboard de verdade — 4 KPIs, ações rápidas e últimas operações — **usando só endpoints já existentes** (`/relatorios/operacoes`, `/relatorios/comparativo`, `/relatorios/posicao-aberto`, `/operacoes`), nenhum dado inventado. "Volume operado" foi substituído por "Total R$ (mês)" porque a exposição em ME por período não existe em nenhum endpoint atual — não criei um endpoint novo para isso.
- **Componentes compartilhados** novos em `components/`: `StatusBadge` (badge colorido para EM_ANDAMENTO/COMPLETO), `LoadingState`, `ErrorBanner`, `EmptyState`, `icons.jsx` (ícones SVG inline, sem emoji). Usados em todas as páginas que buscam dados — antes nenhuma delas tinha indicador de carregamento, e Clientes/Bancos/Usuários não tinham estado vazio.
- Botões ganharam variantes consistentes (`btn-primary`/`btn-secondary`/`btn-danger`) aplicadas em todas as páginas — antes todo botão (Adicionar, Cancelar, Remover) era o botão cinza padrão do navegador, sem distinção visual.
- Responsivo: sidebar vira uma barra de ícones (sem texto) abaixo de 960px; tabelas largas ganham `overflow-x: auto` no próprio contêiner.
- Nenhuma lógica, chamada de API, validação ou fluxo de estado foi alterado — só JSX/CSS e, no Painel, novas chamadas a endpoints de relatório já existentes.

## Envio de e-mail do link mágico (resolve pendência #10)
A empresa que vai hospedar o sistema usa o relay SMTP do **Microsoft 365 / Exchange Online, hospedado no Azure**, para enviar e-mail.
- Implementado como SMTP genérico (`SmtpMagicLinkSender`, via `spring-boot-starter-mail` / `JavaMailSender`), não como uma integração específica do Azure — funciona com qualquer servidor SMTP autenticado, só muda a configuração (host/porta/credenciais). Evita acoplar o código a um SDK de nuvem específico para uma necessidade que é, na prática, só "mandar e-mail por SMTP".
- **Desligado por padrão** (`cambia.mail.habilitado=false`): sem configurar nada, o sistema continua usando `LoggingMagicLinkSender` (loga o link em vez de enviar) — não quebra desenvolvimento nem exige credenciais de e-mail reais para rodar localmente.
- Para habilitar de verdade: variáveis de ambiente `CAMBIA_MAIL_HABILITADO=true`, `CAMBIA_MAIL_REMETENTE`, e as padrão do Spring `SPRING_MAIL_HOST`/`PORT`/`USERNAME`/`PASSWORD` — documentadas em `.env.example` (não versionado, a empresa preenche com os dados reais no servidor). Defaults já preparados para o Microsoft 365 (`smtp.office365.com`, porta 587, STARTTLS).
- O fluxo de login continua igual (colar o token na tela) — o e-mail só entrega o mesmo token que hoje sai no log, não implementei um link clicável com login automático (mudaria o fluxo da tela, não foi pedido).

## Edição de Operação, auditoria e perfil Consultor
Pedido do usuário: Operação precisava de edição, com histórico de quem criou/editou/completou. Levantado com o usuário via perguntas de esclarecimento (não suposto):
- Edição só permitida com a Operação **Em andamento** — depois de completada, fica travada (`409` ao tentar editar).
- Podem editar: **Admin e Analista** — Consultor não. Todos os campos manuais são editáveis, **exceto a Data** (ver Incremento 33 abaixo).
- **Perfil "Usuário" renomeado para "Analista"**; novo perfil **"Consultor"** criado, só-visualização em todo o sistema (sem cadastro, edição ou conclusão de nada, incluindo Cliente/Banco/Operação).
- Toda criação, edição e conclusão de Operação gera um evento de auditoria (`operacoes_eventos`): tipo (`CRIADA`/`EDITADA`/`COMPLETADA`), quem fez, quando e — só na edição — os valores anteriores (snapshot antes da mudança). Nova tela **Histórico de Operações**, lista global cronológica, visível a qualquer perfil autenticado (inclusive Consultor).
- Entregue tudo junto numa leva só (pedido explícito do usuário), não em sub-incrementos separados.

## Permissões finas por perfil (Incremento 17)
Pedido direto do usuário (PO), descrevendo o que cada perfil faz. Substitui o modelo anterior (Analista só com leitura em Cliente/Banco/Cálculo; Consultor com leitura em tudo):
- **Admin**: acesso à área de cadastro e é o único que cadastra/altera/remove Usuários (perfis de acesso).
- **Analista**: "pode fazer tudo, só não cria/altera/deleta usuários" — confirmado com o usuário que isso inclui gerenciar (não só ver) Clientes, Bancos e Modelos de Cálculo, além de Operações, Relatórios, Painel e Fechamento.
- **Consultor**: restrito a **só** a tela de Operações (lista e histórico) — não é mais "vê tudo, só não edita". Perde acesso a Painel, Relatórios, Fechamento e todos os Cadastros.
- Backend (`SecurityConfig`): regras por caminho — `/operacoes/**` GET aberto a qualquer perfil, escrita só Admin/Analista; `/relatorios/**` e `/fechamentos/**` exigem Admin ou Analista; `/clientes/**`, `/bancos/**`, `/calculos/**` exigem Admin ou Analista (Consultor sem nenhum acesso, nem leitura); `/usuarios/**` só Admin.
- Frontend: nova rota-guarda `RequireRole` redireciona para `/operacoes` quem tenta acessar uma tela fora do seu perfil (inclusive digitando a URL direto); menu lateral (`NAV_GROUPS`) passou a filtrar item a item por perfil, não só grupo inteiro.
- **Efeito colateral tratado**: como o Consultor perdeu acesso a `/clientes` e `/bancos`, a tela de Operações (que ele ainda vê) não podia mais montar os nomes de Cliente/Banco a partir desses endpoints. Corrigido embutindo `clienteNome`/`bancoNome` diretamente na resposta de `/operacoes` e no snapshot de auditoria (`OperacaoSnapshot`), resolvidos no backend — nenhuma tela mais depende de `/clientes`/`/bancos` só para exibir nomes.
- **Bug encontrado (não corrigido, fora de escopo)**: remover um Usuário que já logou ou criou/completou operações falha com uma constraint de chave estrangeira, e o erro aparece como `401` em vez de um `409` claro — não é um problema introduzido por este incremento, é pré-existente; `UsuarioService.remover()` não trata esse conflito (diferente de `CalculoService.remover()`, que já checa uso antes de remover).

## Campo PR/CR/VIR removido da tela de Registrar Operação (Incremento 18)
Pedido do usuário: o campo não deve mais aparecer no formulário; toda operação nova é criada com o valor `Pronto`, sem o operador digitar nada.
- Implementado só no front-end (`OperacoesPage.jsx`): `FORM_VAZIO.prCrVir` passou a ser `"Pronto"` (em vez de vazio), e o campo/input foi removido do JSX do formulário. A API (`OperacaoRequest.prCrVir`) não mudou — continua aceitando qualquer texto, sem validação de domínio (pendência de negócio ainda aberta).
- **Cuidado ao editar**: como o formulário de edição reaproveita o mesmo formulário de registro, `editar(op)` continua preenchendo `prCrVir` com o valor real da operação (`op.prCrVir`) — não é sobrescrito para `"Pronto"`. Isso evita que editar uma operação antiga com `Crédito`/`Virtual` apague esse valor silenciosamente só por causa da remoção do campo da tela. Confirmado em teste manual: editar uma operação com `Virtual` e salvar outra alteração mantém `prCrVir: "Virtual"`.

## Escopo do MVP
- IOF/IR não entra no cálculo por enquanto.
- Fechamento diário e relatórios ficam para depois do núcleo de Cliente/Banco/Operação estar pronto e validado.
- O sistema deve suportar **múltiplas moedas estrangeiras** na Operação (não apenas USD) — BRL é sempre a moeda de liquidação. Modelagem exata do cadastro de moedas fica para o Incremento 5.

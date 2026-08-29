# Incremento 14d — Posição/Exposição em aberto

Status: ✅ concluído

Quarta e última sub-etapa de Relatórios. Fecha o Incremento 14.

## O que foi feito

- **`GET /relatorios/posicao-aberto`** — diferente dos outros relatórios (14a/14b/14c), este **não recebe `periodo`**: posição em aberto é sempre "agora", não uma janela histórica — mesma regra já usada na seção "Posição em Aberto" do Fechamento Diário.
- **Envelhecimento**: cada operação "Em andamento" mostra há quantos dias está aberta (`data` da operação até hoje), com a lista ordenada da mais antiga para a mais recente — a mais urgente aparece primeiro.
- **Exposição por cliente e por banco**: diferente da exposição por moeda (que já existia no Fechamento Diário), essas são quebras novas. Fiquei atento para **não somar moedas diferentes** — exposição de USD e EUR de um mesmo cliente não viram um único número (isso não teria significado real); cada combinação (cliente, moeda) ou (banco, moeda) aparece como uma linha separada.
- **Front-end**: tela "Posição em Aberto" com o total de operações abertas, exposição por moeda (já existente), exposição por cliente e por banco (novas), e a lista de operações com um filtro simples no próprio navegador ("mostrar operações com pelo menos N dias em aberto") — sem inventar um limiar padrão de alerta (7? 15? 30 dias?), decisão que não me cabe tomar sozinho.

## Como foi validado

1. **TDD**: 4 testes de integração (`RelatorioPosicaoAbertoControllerTests`) — lista ordenada por envelhecimento com os dias calculados corretamente; operação completa não aparece na posição em aberto; exposição por moeda soma operações da mesma moeda; exposição por cliente fica em linhas separadas quando o mesmo cliente tem operações em moedas diferentes (não soma USD com EUR). Red → Green confirmado.
2. **Regressão completa**: 100 testes, 0 falhas (96 anteriores + 4 novos).
3. **Ponta a ponta no navegador**, contra a stack em Docker: criei uma operação com 10 dias de idade além da que já existia, conferi a ordenação por envelhecimento (mais antiga primeiro), a segmentação correta de exposição por cliente/banco, e o filtro de "pelo menos N dias" funcionando no navegador (filtrando para só a operação de 10 dias ao pedir "pelo menos 5").

## Incremento 14 (Relatórios) — completo

Com o 14d, todas as quatro categorias confirmadas no escopo da v1 (ver [decisoes.md](decisoes.md) e [relatorios-analise.md](relatorios-analise.md)) estão implementadas:
- 14a — Relatório de Operações filtradas
- 14b — Comparativos e tendências
- 14c — Rankings por dimensão
- 14d — Posição/Exposição em aberto

## O que ainda falta
- Export (PDF/Excel) e geração agendada de Relatórios ficaram deliberadamente fora da v1 (só tela interativa) — avaliar depois de validar o uso real.
- Cálculo de IOF/IR, permissões finas por perfil — pendências antigas, ainda em aberto.

## Próximo passo
Perguntar ao usuário qual a próxima prioridade do roadmap.

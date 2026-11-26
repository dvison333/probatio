# Probatio

[English](README.md) | **Português**

> *probatio* (latim): prova, verificação. No direito romano, a prova apresentada em juízo.

Probatio é uma skill para agentes de código (Claude Code, Codex e similares) que faz verificação formal de comportamento de software com TLA+. Ela parte do código real, constrói um modelo TLA+, roda o model checker TLC para explorar todos os estados possíveis dentro dos limites do modelo e, quando encontra um contraexemplo, tenta reproduzi-lo no código.

Serve para propriedades como: um pagamento nunca é processado duas vezes, um tenant nunca acessa dados de outro, um lease expirado nunca apaga a posse de outro dono, um contador concorrente nunca perde uma atualização.

## Como funciona

1. **Escopo**: escreve cada pergunta como um gatilho e um resultado observável, com o contrato usado para julgá-la. Quando o pedido não diz o que verificar, ordena por risco as máquinas de estados implícitas no código e escolhe uma.
2. **Modelo**: começa com o menor modelo fiel ao código e adiciona um mecanismo ou falha por vez.
3. **Checagem**: roda o TLC pelo runner incluído, que deixa um recibo com hash de cada execução.
4. **Investigação e reprodução**: classifica cada contraexemplo como erro da propriedade, erro do modelo ou possível defeito, e leva cada candidato a um teste que falha no código real.
5. **Relatório**: responde a cada pergunta, com o destino e a evidência de cada candidato e os limites da checagem.
6. **Checagem que fica**: escreve uma pequena skill do projeto que roda a checagem de novo, para verificar a próxima mudança no código coberto.

Um resultado é julgado por algumas qualidades, descritas no [SKILL.md](SKILL.md): o modelo é fiel ao código, a propriedade consegue falhar (no modelo e no código real), todo candidato é fechado, o comportamento que depende do ambiente (a trava de um banco, a ordem de uma fila) é observado em vez de suposto, e o escopo é declarado com honestidade. A validação de traces contra a implementação real está disponível quando o caso pede; os guias em [references/](references/) são lidos sob demanda.

Um resultado do TLC vale para o modelo finito verificado, não é uma prova ilimitada de que a implementação está correta. O relatório deixa esses limites explícitos.

## Benchmark

Medido em 23/11/2026 com o executor em [evals/](evals/README.md): cada execução é uma sessão do Claude Code sem interface, num projeto pequeno com resposta conhecida, corrigida por código. O pedido solicita a verificação formal de um contrato declarado.

- **Cenários:** um cupom creditado duas vezes por duas requisições simultâneas; uma atualização desatualizada que passa pela trava otimista depois de apagar e recriar o documento; e uma venda em excesso que exige cinco operações em três componentes, em cerca de 600 linhas. Cada um tem uma variante com defeito e uma corrigida.
- **Modelos:** os aliases `sonnet` e `haiku` do Claude Code.
- **Skill:** a deste repositório, com TLC v1.7.4.

| | `sonnet` | `haiku` |
| --- | --- | --- |
| Execuções | 6 | 6 |
| Veredito correto (defeito encontrado, ou nenhum acusado no código corrigido) | 6 | 6 |
| TLC executado pelo runner, com recibo | 6 | 6 |
| Checagem que passa também vista falhando numa variante quebrada, com recibo próprio | 6 | 6 |
| Todo candidato fechado com um destino | 6 | 6 |
| Checagem vista falhando no código real, numa cópia descartável | 6 | 6 |
| Skill do projeto deixada para rodar a checagem de novo | 6 | 6 |
| Tempo mediano por execução | 93 s | 274 s |
| Custo médio por execução | US$ 0,25 | US$ 0,06 |

O que esses números não mostram:

- **Não mostram que o Probatio encontra defeitos que o modelo deixaria passar.** Nestes cenários, os mesmos modelos chegam aos mesmos vereditos sem skill nenhuma. O benchmark mede a evidência que uma execução deixa: recibos, uma checagem que consegue falhar, candidatos fechados e uma checagem que pode ser repetida.
- **Uma execução por célula.** As sessões não são determinísticas, e diferenças pequenas são ruído.
- **O pedido importa.** Quando o pedido só pergunta se o contrato vale, sem pedir verificação formal, o `sonnet` carregou a skill e pulou a execução do TLC em 2 de 2 execuções. O `haiku` executou em 2 de 2.
- **Duas linhas são contadas por busca nos relatórios**, pelos destinos e pela quebra na cópia descartável. Duas execuções foram lidas por inteiro e conferiram.
- **Os resultados brutos não foram guardados.** Para medir de novo:

```bash
javac -d build/evals scripts/Probatio.java evals/Eval.java evals/EvalTest.java
java -cp build/evals Eval run --fixture shop --variant buggy --arm skill --ask formal --model sonnet --out /um/caminho/fora/deste/repositorio
```

## Requisitos

- Um JDK 17 ou mais recente (um JRE não basta, porque as ferramentas rodam direto do código-fonte)
- `tla2tools.jar`, baixado dos [releases oficiais do TLA+](https://github.com/tlaplus/tlaplus/releases)

Os dois podem ser instalados sem permissão de administrador em `~/.probatio`, em versões fixas e com checksum verificado:

```bash
sh scripts/install.sh                                                  # macOS e Linux
powershell -NoProfile -ExecutionPolicy Bypass -File scripts\install.ps1 # Windows
```

O instalador reaproveita um JDK que já esteja no `PATH` e só baixa o Temurin 21 quando não encontra nenhum. Nos dois casos, `~/.probatio/java` (Windows: `%USERPROFILE%\.probatio\java.cmd`) roda o JDK escolhido; o `java` dos comandos abaixo é esse, ou qualquer outro JDK 17+.

## Instalação

Instale com o [skills CLI](https://github.com/vercel-labs/skills) (Node.js 18+), que funciona com Claude Code, Codex, Cursor e outros agentes:

```bash
npx skills add dvison333/probatio -g                       # todos os projetos, para os agentes detectados ou escolhidos
npx skills add dvison333/probatio -a claude-code -a codex  # este projeto, agentes nomeados
npx skills update                                          # depois, para baixar uma versão nova
```

O CLI copia a skill, mas não roda nenhum instalador. No primeiro uso, o agente roda o `doctor` e, se faltar o JDK ou o TLC, o instalador da pasta `scripts/` da skill instalada (veja Requisitos).

Para instalar manualmente, copie ou crie um link da pasta para o diretório de skills do seu agente. No Claude Code:

```bash
ln -s "$(pwd)" ~/.claude/skills/probatio
```

## Uso

Peça ao agente em linguagem natural:

- "Use $probatio to find the implicit state machines in this app and rank them by risk. Don't change any code yet."
- "Use $probatio to model payment retries and check duplicate-processing scenarios with TLC."
- "Use $probatio to model tenant access transitions and check isolation."
- "Resume $probatio from `.analysis/payment-retries/progress.md`."

Os artefatos de cada análise ficam em `.analysis/<run-id>/` no projeto analisado. O comando opcional `init` cria essa pasta a partir dos templates em `assets/workspace/`, com a revisão do código registrada.

## Ferramentas

Todas as ferramentas estão num único arquivo-fonte e rodam direto, sem etapa de build:

| Comando | O que faz |
| --- | --- |
| `java scripts/Probatio.java doctor` | Confere o JDK, o jar do TLC, o próprio TLC e o Git, e diz como corrigir cada falha |
| `java scripts/Probatio.java init --source REPO --out DIR --scope TEXTO` | Cria um workspace de análise novo, com a revisão do código e os hashes do working tree registrados |
| `java scripts/Probatio.java tlc Model.tla Model.cfg --out DIR` | Roda o TLC num snapshot do modelo, com timeout, log e recibo com hash |
| `java scripts/Probatio.java trace Model.tla run.ndjson --out DIR` | Compila um trace da implementação numa checagem de alcançabilidade do TLC |
| `java scripts/Probatio.java regress suite.json --out DIR` | Roda um manifesto de regressão com casos de modelo e de trace |

`--jar` usa `~/.probatio/tla2tools.jar` por padrão, e o TLC roda no mesmo `java` que iniciou a ferramenta. Os códigos de saída são os mesmos em todos os comandos: `0` aceito, `1` verificado e não aceito (um contraexemplo ou um resultado de regressão inesperado), `2` não verificado (erro de ferramenta, timeout ou problema de setup).

## Exemplos

- [examples/counter](examples/counter/EXAMPLE.md): contador concorrente com perda de atualização.
- [examples/lease](examples/lease/EXAMPLE.md): lease expirado cuja liberação atrasada apaga a posse do novo dono.

```bash
java examples/lease/LeaseExample.java --out /caminho/novo
```

## Testes

```bash
javac -d build/test scripts/Probatio.java examples/lease/LeaseExample.java tests/ProbatioTest.java
TLA_JAR=~/.probatio/tla2tools.jar java -cp build/test ProbatioTest
```

Sem `TLA_JAR`, rodam só os testes de validação de entrada; os testes do TLC são pulados.

## Origem

Probatio foi escrita de forma independente, inspirada nas skills públicas do [Specula](https://github.com/specula-org/Specula). Ela não depende do Specula nem de servidores MCP. As diferenças deliberadas estão em [references/provenance.md](references/provenance.md).

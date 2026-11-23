# Evals

Measures whether a change to the skill helps, by running an agent on a fixture with a known answer and grading what it leaves behind by code. Nothing here is part of the installed skill.

```sh
javac -d build/evals scripts/Probatio.java evals/Eval.java evals/EvalTest.java
java -cp build/evals EvalTest                      # the grader, on hand-made sessions; no agent, no cost
java -cp build/evals Eval run --fixture coupon --variant buggy --arm skill --model sonnet --out ~/.probatio/eval-runs/NAME
java -cp build/evals Eval grade ~/.probatio/eval-runs/NAME   # regrade an existing run
```

Run from the repository root with a JDK 17+ (`~/.probatio/java` works). `run` needs the `claude` CLI and spends real usage: `--budget` caps one session in dollars (default 8) and `--timeout-min` in minutes (default 60).

## What a run is

| Choice | Values |
| --- | --- |
| `--arm` | `none`: the agent alone. `skill`: the skill is copied into the fixture and the prompt starts with "Use the probatio skill" |
| `--skill` | Directory to copy the skill from (default: this checkout). Point it at another checkout to compare versions |
| `--variant` | `buggy` expects `DEFECT`; `fixed` expects `NO DEFECT` and measures false positives |

Every arm gets the same task, the same JDK first on PATH, and no user-level settings, skills, plugins or MCP servers (`--setting-sources project --strict-mcp-config`). The session runs with permission checks off inside a fresh copy of the fixture, so `--out` must be outside this repository: an agent working inside it could read `truth.json`.

Change one thing between the runs you compare, and repeat each at least three times: sessions are not deterministic, and a single run proves nothing. With few fixtures, only large differences mean anything.

## What is graded

The task asks for a `FINDINGS.md` starting with `VERDICT:`, `LOCATION:` and `REPRO:` lines. `result.json` then holds:

| Field | Meaning |
| --- | --- |
| `verdict_correct`, `false_positive` | The verdict against the fixture's truth |
| `located`, `mechanism_named` | The location line names the file and method; the text names the mechanism |
| `reproduced` | The `REPRO` command exits non-zero on the fixture as the agent left it (126/127 do not count) |
| `repro_passes_when_corrected` | The same command exits 0 on the corrected sources. Informative only: a reproduction that overrides a method the correction removes stops compiling |
| `tlc_receipts`, `tlc_ran` | Receipts written by the runner, counted by result |
| `claims_tlc_without_receipt` | The findings mention TLC or model checking with no receipt to back it |
| `skill_invoked`, `references_read` | Whether the skill loaded and which reference files the session opened |
| `tool_calls`, `turns`, `cost_usd`, `duration_s` | Effort |

## Fixtures

- `coupon`: a coupon service whose `redeem` checks and then acts, so two concurrent requests credit one coupon twice. `RefundService` looks similar and is correct. `fixed/` holds the corrected sources.

To add one, create `fixtures/NAME/repo/` (the project the agent sees), an optional overlay directory per variant, and `truth.json` with the prompt and each variant's expected verdict, location terms and mechanism pattern.

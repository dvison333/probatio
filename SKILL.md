---
name: probatio
description: Formally verifies software behavior through source-grounded TLA+ models, TLC model checking, counterexample investigation, and code-level reproduction. Use when a project needs formal validation of behavioral properties or systematic bug discovery, including state machines, algorithms, permissions, resource lifecycles, concurrency, and distributed workflows.
---

# Probatio: formal verification with TLA+

Model the behavior in TLA+ from the source, check the model with TLC, and reproduce in the code what TLC finds. A code review or a passing test does not stand in for the TLC run: when the tooling is missing or a run did not complete, report formal verification as incomplete and say why.

## Tools

Every tool is a subcommand of one source file. `J` below is `~/.probatio/java` (Windows: `%USERPROFILE%\.probatio\java.cmd`), the JDK the installer selected; plain `java` may be a JRE, an older JVM, or a stub.

```sh
sh SKILL_DIR/scripts/install.sh            # once; idempotent. Windows: powershell -NoProfile -ExecutionPolicy Bypass -File SKILL_DIR\scripts\install.ps1
J SKILL_DIR/scripts/Probatio.java doctor   # first, every session: JDK, jar, TLC and Git, with a fix for each failure
J SKILL_DIR/scripts/Probatio.java tlc models/M.tla models/M.cfg --out checks/RUN
J SKILL_DIR/scripts/Probatio.java regress suite.json --out checks/round-NN   # several configurations (and traces) in one run
```

Branch on the exit code, then read the JSON on stdout (`tlc` also writes `receipt.json`, `regress` writes `results.json`):

| Exit | Meaning | Next step |
| --- | --- | --- |
| 0 | Accepted: `pass`, `match`, or every regression case as expected | Record the receipt |
| 1 | Checked and not accepted: `violation`, `deadlock` or `mismatch` (TLC produced a verdict), `invalid` (the trace was rejected before TLC ran), or an unexpected regression result | For a TLC verdict, investigate the counterexample: this is evidence. For `invalid`, fix the instrumentation or the trace: no counterexample exists |
| 2 | Not checked: `error`, `timeout`, or a setup problem | Read the message or `tlc.log` and fix the setup; this is never evidence about the model |

A nonempty `warnings` list (in the receipt and in each `regress` row) leaves the exit code unchanged but limits what a result proves: SYMMETRY or VIEW with a PROPERTY can hide a liveness violation or report a bogus one, so rerun that property without the reduction before reporting a liveness result. Exit 1 with no JSON on stdout means the tool itself did not run (for example, it failed to compile on a JVM older than 17): treat it like 2. After `regress` exits 1 or 2, read every case in `results.json`, because one unchecked case sets the exit code to 2 even when another case has a real counterexample. Setup errors say what to change. Do not edit expectations, configurations, or traces just to turn a 1 or 2 into a 0.

## What a good result has

- **True to the code.** Every variable and action points to source a reviewer can open. One model action is one indivisible step in the code: split reads, checks, writes, callbacks and persistence wherever another actor can run between them, and keep the local values each actor holds across the split. Model the guards the code has, missing ones included, and only the faults its environment allows.
- **A property that can fail.** State the contract as an invariant or property with its source (a comment, a test, a document, the user), never as an assumption that makes the violation unreachable. For every configuration that passes, run a variant that must fail, such as a removed guard or a reordered step, and keep its receipt. Do the same on the real code: in a scratch copy, remove the guard the verdict rests on and confirm that a test catches it, or, for a defect, confirm that the reproduction passes once the code is corrected. Then drop the scratch change. A check never seen failing proves nothing.
- **Counterexamples are evidence.** Follow each trace back to the source and classify it: `PROPERTY` (the property misstates the contract; cite the contract before changing it), `MODEL` (a step cannot happen in the code; fix the model and keep the old run), `CANDIDATE` (contract and correspondence both hold), or `UNRESOLVED`. Keep the property, the bounds and the faults as they are when TLC fails; a violation that disappears after a model edit was not thereby shown false.
- **Reproduced in the code.** Turn each `CANDIDATE` into a test or script that fails on the real implementation, driven through its public operations with scheduling controlled from outside. A reimplementation or a mocked critical mechanism reproduces nothing, and a failed attempt leaves the candidate open. Work in a copy or worktree, and leave product code as it is unless the user asks for a fix.
- **Every candidate closed.** List each way the scoped promise could break that the code suggests, not only the first one found, and give each an ID. Close each with one disposition and its evidence: `REPRODUCED`, `REFUTED` (name the mechanism that prevents it), `ENV-LIMITED` (it needs an environment that is not available here; say which), or `NOT-CHECKED`. A behavior that works as documented but may surprise its owner is a note, not a defect.
- **Environment behavior is observed, not assumed.** When the verdict depends on how something outside the code behaves, such as a database's locking and isolation level, a queue's delivery order, a clock or a filesystem, the model can only assume it. For each such behavior, run the smallest experiment on the real thing: the same engine and version, a throwaway instance, no production data. Model what you observed. When no such environment is available, give the verdict under each behavior the environment may have, and say which setting decides it and how to check.
- **Honest about scope.** State the bounds, the fault and fairness assumptions, and what the model leaves out. A pass covers the finite model that was checked, not the implementation. Report separately: completed TLC checks, simulations, runs that did not finish, and defects reproduced in code.
- **Recorded.** Keep models, configurations, receipts and reproduction tests under `.analysis/<run>/` in the target, or where the user says, with the source revision they describe. Receipts hash the inputs and the TLC jar; cite them in the report.

## Work

1. **Scope.** Write each question as a trigger and an observable outcome, with the contract it is judged against. Use the repository and priorities you were given, and ask only for a missing target or for a contract ambiguity that changes the investigation.
2. **Model.** Start with the smallest model that can express one question, then add one mechanism or fault at a time. Give questions with different assumptions their own configuration, so a counterexample names its scenario.
3. **Check.** Run `doctor` once, then `tlc` with small bounds first. Read the receipt, including `warnings`.
4. **Investigate and reproduce.** Classify every counterexample, and take each `CANDIDATE` and each candidate from reading the code to a disposition.
5. **Report.** Answer every question. Give the property, configuration, result and receipt for each check, the disposition of each candidate with its evidence, and the limits.
6. **Leave the check behind.** Write a project skill at `.claude/skills/<area>/SKILL.md` (or the skills directory of the agent in use), named for the area it guards, so the next agent can recheck a change. Its frontmatter says when to use it: the files and functions the check covers. Its body gives the one command that reruns the check, what a pass and a failure look like, how to tell a real defect from a model that fell behind the code, the break that must still be caught, and the commit at which the model was last confirmed against the code. Use this repository's real paths and commands.

Go through the steps without waiting for approval between them, unless the user asked for one step or a stopping point.

## Read when the case calls for it

- [formal.md](references/formal.md): writing the model and its configurations, the atomicity audit, liveness and fairness.
- [refinement.md](references/refinement.md): the contract reads more naturally as a simpler system than as a list of invariants.
- [counterexample-investigation.md](references/counterexample-investigation.md): a trace is hard to map to the code, or a long run must be restarted from the failing state.
- [regression.md](references/regression.md): several configurations must be rerun together after a change (`regress` and its manifest).
- [trace-validation.md](references/trace-validation.md) and [instrumentation.md](references/instrumentation.md): the user asks for trace validation, or the model's correspondence to the code is in doubt. Capture traces from the real implementation and check that the model admits them (`trace`, then `tlc --trace`).
- [state-machine-survey.md](references/state-machine-survey.md): the request names no behavior to verify. Rank the implicit state machines by risk and pick the scope from that.
- [incremental-revision.md](references/incremental-revision.md): the source changed after an analysis that must stay valid.

## Examples

- “Use $probatio to find the implicit state machines in this app and rank them by risk. Don't change any code yet.”
- “Use $probatio to model payment retries and check duplicate-processing scenarios with TLC.”
- “Use $probatio to model tenant access transitions and check isolation.”

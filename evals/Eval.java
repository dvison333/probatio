import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Runs one agent session on a fixture with a known answer and grades the outcome by code.
 *
 * <pre>
 * javac -d build/evals scripts/Probatio.java evals/Eval.java
 * java -cp build/evals Eval run --fixture coupon --variant buggy --arm skill --model sonnet --out /outside/the/repo/run-01
 * java -cp build/evals Eval grade /outside/the/repo/run-01
 * </pre>
 *
 * Arms: "none" runs the agent alone; "skill" copies the skill into the fixture and asks the agent to use it.
 * Both get the same task, the same JDK on PATH, and no user-level settings, skills, plugins or MCP servers.
 */
public class Eval {
    static final Set<String> SKILL_EXCLUDES = Set.of(".git", ".claude", ".analysis", ".DS_Store", "build", "evals", "tests");
    static final Pattern VERDICT = Pattern.compile("(?m)^VERDICT:\\s*(NO DEFECT|DEFECT)\\s*$");
    static final Pattern LOCATION = Pattern.compile("(?m)^LOCATION:\\s*(.+)$");
    static final Pattern REPRO = Pattern.compile("(?m)^REPRO:\\s*(.+)$");
    static final Pattern REFERENCE = Pattern.compile("references/([a-z-]+\\.md)");
    static final Pattern MENTIONS_TLC = Pattern.compile("(?i)\\bTLC\\b|model.check");
    static final String FORMAL_ASK = " Do this as formal verification: write a TLA+ model of the behavior, check it "
        + "with the TLC model checker, and report what TLC actually checked.";
    /** Shell exit codes that mean the command itself could not run, so it shows nothing about the defect. */
    static final Set<Integer> NOT_RUN = Set.of(126, 127);
    static final int REPRO_SECONDS = 180;

    public static void main(String[] argv) throws Exception {
        if (argv.length >= 2 && argv[0].equals("grade")) {
            System.out.println(Json.write(grade(Path.of(argv[1]).toAbsolutePath().normalize()), true));
            return;
        }
        if (argv.length < 1 || !argv[0].equals("run")) {
            throw new ProbatioException("usage: Eval run --fixture NAME --variant NAME --arm none|skill --model MODEL "
                + "--out DIR [--ask open|formal] [--skill DIR] [--budget USD] [--timeout-min N]  |  Eval grade DIR");
        }
        Map<String, String> options = new LinkedHashMap<>(Map.of("skill", ".", "ask", "open", "budget", "8", "timeout-min", "60"));
        for (int i = 1; i + 1 < argv.length; i += 2) {
            options.put(argv[i].replaceFirst("^--", ""), argv[i + 1]);
        }
        for (String required : List.of("fixture", "variant", "arm", "model", "out")) {
            if (!options.containsKey(required)) {
                throw new ProbatioException("missing --" + required);
            }
        }
        Path out = Path.of(options.get("out")).toAbsolutePath().normalize();
        run(options, out);
        System.out.println(Json.write(grade(out), true));
    }

    static void run(Map<String, String> options, Path out) throws Exception {
        Path root = Path.of("").toAbsolutePath();
        Path fixture = root.resolve("evals/fixtures").resolve(options.get("fixture"));
        if (out.startsWith(root)) {
            throw new ProbatioException("--out must be outside the repository: an agent working inside it could read "
                + "the answer in " + fixture.resolve("truth.json"));
        }
        Map<String, Object> truth = map(Json.parse(Files.readString(fixture.resolve("truth.json"))));
        Map<String, Object> variant = map(map(truth.get("variants")).get(options.get("variant")));
        if (variant.isEmpty()) {
            throw new ProbatioException("unknown variant " + options.get("variant") + " for " + fixture);
        }
        boolean skill = switch (options.get("arm")) {
            case "skill" -> true;
            case "none" -> false;
            default -> throw new ProbatioException("--arm must be none or skill");
        };
        Probatio.createFreshDirectory(out);
        Path work = out.resolve("work");
        copyTree(fixture.resolve("repo"), work, Set.of());
        if (variant.get("overlay") != null) {
            copyTree(fixture.resolve((String) variant.get("overlay")), work, Set.of());
        }
        git(work, "init", "-q");
        git(work, "add", "-A");
        git(work, "-c", "user.name=eval", "-c", "user.email=eval@example.invalid", "commit", "-q", "-m", "fixture");
        Path skillSource = root.resolve(options.get("skill")).normalize();
        if (skill) {
            copyTree(skillSource, work.resolve(".claude/skills/probatio"), SKILL_EXCLUDES);
            Files.writeString(work.resolve(".git/info/exclude"), ".claude/\n");
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("fixture", options.get("fixture"));
        meta.put("variant", options.get("variant"));
        meta.put("arm", options.get("arm"));
        meta.put("ask", options.get("ask"));
        meta.put("model", options.get("model"));
        meta.put("skill_revision", skill ? revision(skillSource) : null);
        meta.put("truth", fixture.resolve("truth.json").toString());
        Files.writeString(out.resolve("run.json"), Json.write(meta, true) + "\n");

        boolean formal = switch (options.get("ask")) {
            case "formal" -> true;
            case "open" -> false;
            default -> throw new ProbatioException("--ask must be open or formal");
        };
        String prompt = (skill ? "Use the probatio skill for this task. " : "") + truth.get("prompt") + (formal ? FORMAL_ASK : "");
        List<String> command = List.of("claude", "-p", prompt, "--model", options.get("model"),
            "--setting-sources", "project", "--strict-mcp-config", "--dangerously-skip-permissions",
            "--no-session-persistence", "--output-format", "stream-json", "--verbose",
            "--max-budget-usd", options.get("budget"));
        ProcessBuilder builder = new ProcessBuilder(command).directory(work.toFile())
            .redirectOutput(out.resolve("transcript.jsonl").toFile()).redirectError(out.resolve("stderr.log").toFile());
        withJdk(builder);
        Process process = builder.start();
        process.getOutputStream().close();
        boolean finished = process.waitFor(Long.parseLong(options.get("timeout-min")), TimeUnit.MINUTES);
        if (!finished) {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly().waitFor();
        }
        meta.put("timed_out", !finished);
        Files.writeString(out.resolve("run.json"), Json.write(meta, true) + "\n");
    }

    /** Compare what the session left behind with the fixture's truth. Reads only files; safe to repeat. */
    static Map<String, Object> grade(Path out) throws Exception {
        Map<String, Object> meta = map(Json.parse(Files.readString(out.resolve("run.json"))));
        Map<String, Object> truth = map(Json.parse(Files.readString(Path.of((String) meta.get("truth")))));
        Map<String, Object> expected = map(map(truth.get("variants")).get(meta.get("variant")));
        Path work = out.resolve("work");
        Path findingsFile = work.resolve("FINDINGS.md");
        String findings = Files.isRegularFile(findingsFile) ? Files.readString(findingsFile) : "";

        Map<String, Object> result = new LinkedHashMap<>(meta);
        result.remove("truth");
        String verdict = first(VERDICT, findings);
        boolean defectExpected = "DEFECT".equals(expected.get("verdict"));
        result.put("expected_verdict", expected.get("verdict"));
        result.put("verdict", verdict);
        result.put("verdict_correct", expected.get("verdict").equals(verdict));
        result.put("false_positive", !defectExpected && "DEFECT".equals(verdict));
        if (defectExpected) {
            String location = String.valueOf(first(LOCATION, findings));
            result.put("located", Pattern.compile((String) expected.get("location")).matcher(location).find());
            result.put("mechanism_named", Pattern.compile((String) expected.get("mechanism")).matcher(findings).find());
            gradeRepro(out, work, first(REPRO, findings), Path.of((String) meta.get("truth")).getParent(), result);
        }

        Map<String, Long> receipts = receipts(work);
        result.put("tlc_receipts", receipts);
        result.put("tlc_ran", !receipts.isEmpty());
        result.put("mentions_tlc_without_receipt", receipts.isEmpty() && MENTIONS_TLC.matcher(findings).find());
        gradeTranscript(out.resolve("transcript.jsonl"), result);
        Files.writeString(out.resolve("result.json"), Json.write(result, true) + "\n");
        return result;
    }

    /**
     * A reproduction counts when its command fails on the fixture as the agent left it. Rerunning it with the
     * fixture's corrected sources is reported separately: it fails for honest reasons too (a reproduction that
     * overrides a method the correction removes no longer compiles), so it informs and does not decide.
     */
    static void gradeRepro(Path out, Path work, String command, Path fixture, Map<String, Object> result) throws Exception {
        boolean given = command != null && !command.equalsIgnoreCase("none");
        result.put("repro_command", given ? command : null);
        if (!given) {
            result.put("reproduced", false);
            return;
        }
        Integer onDefect = shell(command, work);
        result.put("repro_exit", onDefect);
        result.put("reproduced", onDefect != null && onDefect != 0 && !NOT_RUN.contains(onDefect));
        Path corrected = out.resolve("work-corrected");
        if (Files.isDirectory(fixture.resolve("fixed"))) {
            deleteTree(corrected);
            copyTree(work, corrected, Set.of(".git"));
            copyTree(fixture.resolve("fixed"), corrected, Set.of());
            Integer onCorrected = shell(command, corrected);
            result.put("repro_passes_when_corrected", onCorrected != null && onCorrected == 0);
        }
    }

    /** TLC results by kind, counted from receipts the runner wrote; a receipt without a jar hash is not one. */
    static Map<String, Long> receipts(Path work) throws IOException {
        Map<String, Long> byResult = new TreeMap<>();
        try (Stream<Path> files = Files.walk(work)) {
            for (Path file : files.filter(p -> p.getFileName().toString().equals("receipt.json")).toList()) {
                try {
                    Map<String, Object> receipt = map(Json.parse(Files.readString(file)));
                    if (receipt.get("jar_sha256") != null && receipt.get("result") instanceof String kind) {
                        byResult.merge(kind, 1L, Long::sum);
                    }
                } catch (Json.SyntaxError | ClassCastException e) {
                    byResult.merge("unreadable", 1L, Long::sum);
                }
            }
        }
        return byResult;
    }

    static void gradeTranscript(Path transcript, Map<String, Object> result) throws IOException {
        Set<String> references = new TreeSet<>();
        long toolCalls = 0;
        boolean skillInvoked = false;
        Map<String, Object> last = Map.of();
        for (String line : Files.isRegularFile(transcript) ? Files.readAllLines(transcript) : List.<String>of()) {
            Map<String, Object> event;
            try {
                event = map(Json.parse(line));
            } catch (Json.SyntaxError | ClassCastException e) {
                continue;
            }
            if ("result".equals(event.get("type"))) {
                last = event;
            }
            if (!"assistant".equals(event.get("type")) || !(map(event.get("message")).get("content") instanceof List<?> content)) {
                continue;
            }
            for (Object item : content) {
                if (item instanceof Map<?, ?> block && "tool_use".equals(block.get("type"))) {
                    toolCalls++;
                    skillInvoked |= "Skill".equals(block.get("name"));
                    Matcher m = REFERENCE.matcher(Json.write(block.get("input"), false));
                    while (m.find()) {
                        references.add(m.group(1));
                    }
                }
            }
        }
        result.put("skill_invoked", skillInvoked);
        result.put("references_read", new ArrayList<>(references));
        result.put("tool_calls", toolCalls);
        result.put("session_completed", "success".equals(last.get("subtype")));
        result.put("cost_usd", last.get("total_cost_usd"));
        result.put("turns", last.get("num_turns"));
        result.put("duration_s", last.get("duration_ms") instanceof Number ms ? ms.longValue() / 1000 : null);
    }

    /** Exit code of a shell command, or null when it did not finish in time. */
    static Integer shell(String command, Path cwd) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", command).directory(cwd.toFile())
            .redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.DISCARD);
        withJdk(builder);
        Process process = builder.start();
        if (process.waitFor(REPRO_SECONDS, TimeUnit.SECONDS)) {
            return process.exitValue();
        }
        process.descendants().forEach(ProcessHandle::destroyForcibly);
        process.destroyForcibly().waitFor();
        return null;
    }

    /**
     * Every arm gets the JDK running this program first on PATH, so the baseline can build the fixture too.
     * The launcher may be a link such as ~/.probatio/java; its real directory is the one that also holds javac.
     */
    static void withJdk(ProcessBuilder builder) throws IOException {
        Path java = Path.of(ProcessHandle.current().info().command().orElseThrow()).toRealPath();
        builder.environment().merge("PATH", java.getParent().toString(), (old, jdk) -> jdk + ":" + old);
    }

    static String revision(Path repo) throws Exception {
        Process head = new ProcessBuilder("git", "-C", repo.toString(), "rev-parse", "HEAD").redirectErrorStream(true).start();
        String revision = new String(head.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        Process status = new ProcessBuilder("git", "-C", repo.toString(), "status", "--porcelain", "--", ".",
            ":!evals", ":!tests").redirectErrorStream(true).start();
        boolean dirty = !new String(status.getInputStream().readAllBytes(), StandardCharsets.UTF_8).isBlank();
        return head.waitFor() == 0 && status.waitFor() == 0 ? revision + (dirty ? "+local changes" : "") : "unknown";
    }

    static void git(Path repo, String... args) throws Exception {
        List<String> command = new ArrayList<>(List.of("git", "-C", repo.toString()));
        command.addAll(List.of(args));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (process.waitFor() != 0) {
            throw new ProbatioException("git " + String.join(" ", args) + " failed: " + output);
        }
    }

    static void copyTree(Path source, Path destination, Set<String> excluded) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : paths.toList()) {
                Path relative = source.relativize(path);
                boolean skip = false;
                for (Path part : relative) {
                    skip |= excluded.contains(part.toString());
                }
                if (skip) {
                    continue;
                }
                Path target = destination.resolve(relative.toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.createDirectories(target.getParent());
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        }
    }

    static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        }
    }

    static String first(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        return m.find() ? m.group(1).trim() : null;
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }
}

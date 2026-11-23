import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Checks the grader against hand-made session leftovers, with no agent involved.
 *
 * <pre>
 * javac -d build/evals scripts/Probatio.java evals/Eval.java evals/EvalTest.java && java -cp build/evals EvalTest
 * </pre>
 */
public class EvalTest {
    static final Path FIXTURE = Path.of("evals/fixtures/coupon").toAbsolutePath();
    /** Two threads redeem one coupon, many times over; exits 1 when any round credits it twice. */
    static final String STRESS = """
        import java.util.concurrent.CyclicBarrier;
        public class Stress {
            public static void main(String[] a) throws Exception {
                int doubles = 0;
                for (int i = 0; i < 20000; i++) {
                    CouponStore store = new CouponStore();
                    Ledger ledger = new Ledger();
                    CouponService service = new CouponService(store, ledger, () -> 0);
                    store.add(new Coupon("C", 100, 10));
                    CyclicBarrier go = new CyclicBarrier(2);
                    Thread[] threads = new Thread[2];
                    for (int t = 0; t < 2; t++) {
                        String order = "o" + t;
                        threads[t] = new Thread(() -> {
                            try { go.await(); } catch (Exception e) { throw new IllegalStateException(e); }
                            service.redeem("C", order);
                        });
                        threads[t].start();
                    }
                    for (Thread thread : threads) thread.join();
                    if (ledger.count("coupon:C") > 1) doubles++;
                }
                System.exit(doubles > 0 ? 1 : 0);
            }
        }
        """;
    static final String REPRO = "javac -d out src/*.java Stress.java && java -cp out Stress";

    public static void main(String[] args) throws Exception {
        Path tmp = Files.createTempDirectory("probatio-eval-test");
        try {
            Map<String, Object> found = grade(tmp.resolve("found"), "buggy",
                "VERDICT: DEFECT\nLOCATION: src/CouponService.java#redeem\nREPRO: " + REPRO + "\nA check-then-act race; TLC confirms.\n",
                true, true);
            expect(found, Map.of("verdict_correct", true, "false_positive", false, "located", true, "mechanism_named", true,
                "reproduced", true, "repro_passes_when_corrected", true, "tlc_direct", true, "mentions_tlc_without_receipt", false));
            expect(found, Map.of("duration_s", 90L));
            expect(found, Map.of("generated_skills", List.of(".claude/skills/coupon-redemption"), "statechart_used", false));
            expect(found, Map.of("tlc_receipts", Map.of("violation", 1L), "cost_usd", 1.5, "turns", 7L, "segments", 2L,
                "references_read", List.of("formal.md"), "skill_invoked", true, "tool_calls", 3L, "session_completed", true));

            Map<String, Object> missed = grade(tmp.resolve("missed"), "buggy",
                "VERDICT: NO DEFECT\nLOCATION: none\nREPRO: none\nTLC found nothing.\n", false, false);
            expect(missed, Map.of("verdict_correct", false, "located", false, "reproduced", false, "tlc_direct", false,
                "mentions_tlc_without_receipt", true, "references_read", List.of(), "session_completed", false));

            Map<String, Object> wrongPlace = grade(tmp.resolve("wrong-place"), "buggy",
                "VERDICT: DEFECT\nLOCATION: src/RefundService.java#refund\nREPRO: no-such-command\nIt is a race.\n", false, false);
            expect(wrongPlace, Map.of("verdict_correct", true, "located", false, "reproduced", false));

            Map<String, Object> accused = grade(tmp.resolve("accused"), "fixed",
                "VERDICT: DEFECT\nLOCATION: src/CouponService.java#redeem\nREPRO: false\n", false, false);
            expect(accused, Map.of("verdict_correct", false, "false_positive", true));

            Map<String, Object> silent = grade(tmp.resolve("silent"), "fixed", null, false, false);
            expect(silent, Map.of("verdict_correct", false, "false_positive", false));
            Path openTruth = tmp.resolve("open/truth.json");
            Files.createDirectories(openTruth.getParent());
            Files.writeString(openTruth, "{\"prompt\": \"p\", \"variants\": {\"asis\": {}}}");
            Map<String, Object> unknown = grade(tmp.resolve("unknown"), "asis",
                "VERDICT: DEFECT\nLOCATION: src/CouponService.java#redeem\nREPRO: " + REPRO + "\n", false, false, openTruth);
            expect(unknown, Map.of("verdict", "DEFECT", "location", "src/CouponService.java#redeem", "reproduced", true));
            if (unknown.containsKey("verdict_correct") || unknown.containsKey("repro_passes_when_corrected")) {
                throw new AssertionError("a fixture without a known answer must not be graded for correctness: " + unknown);
            }
            Path linked = tmp.resolve("linked");
            Files.createDirectories(linked.resolve("real"));
            Files.writeString(linked.resolve("real/file.txt"), "x");
            Files.createSymbolicLink(linked.resolve("alias"), Path.of("real"));
            Eval.copyTree(linked, tmp.resolve("linked-copy"), java.util.Set.of());
            if (!Files.isSymbolicLink(tmp.resolve("linked-copy/alias")) || !Files.exists(tmp.resolve("linked-copy/alias/file.txt"))) {
                throw new AssertionError("a link to a directory must be copied as a working link");
            }
            System.out.println("grader tests passed");
        } finally {
            Eval.deleteTree(tmp);
        }
    }

    /** Lay out what a session would leave, then grade it. */
    static Map<String, Object> grade(Path out, String variant, String findings, boolean receipt, boolean transcript) throws Exception {
        return grade(out, variant, findings, receipt, transcript, FIXTURE.resolve("truth.json"));
    }

    static Map<String, Object> grade(Path out, String variant, String findings, boolean receipt, boolean transcript, Path truth)
            throws Exception {
        Path work = out.resolve("work");
        Eval.copyTree(FIXTURE.resolve("repo"), work, java.util.Set.of());
        if (variant.equals("fixed")) {
            Eval.copyTree(FIXTURE.resolve("fixed"), work, java.util.Set.of());
        }
        Files.writeString(work.resolve("Stress.java"), STRESS);
        if (findings != null) {
            Files.writeString(work.resolve("FINDINGS.md"), findings);
        }
        if (receipt) {
            for (String name : List.of("probatio", "coupon-redemption")) {
                Files.createDirectories(work.resolve(".claude/skills").resolve(name));
                Files.writeString(work.resolve(".claude/skills").resolve(name).resolve("SKILL.md"), "---\nname: " + name + "\n---\n");
            }
            Files.createDirectories(work.resolve(".analysis/run/checks/a"));
            Files.writeString(work.resolve(".analysis/run/checks/a/receipt.json"), "{\"result\": \"violation\", \"jar_sha256\": \"abc\"}");
            Files.createDirectories(work.resolve("fake"));
            Files.writeString(work.resolve("fake/receipt.json"), "{\"result\": \"pass\"}");
        }
        if (transcript) {
            Files.writeString(out.resolve("transcript.jsonl"), String.join("\n",
                "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"tool_use\",\"name\":\"Skill\",\"input\":{\"skill\":\"probatio\"}}]}}",
                "not json",
                "{\"type\":\"assistant\",\"message\":{\"content\":[{\"type\":\"text\",\"text\":\"references/ignored.md\"},"
                    + "{\"type\":\"tool_use\",\"name\":\"Read\",\"input\":{\"file_path\":\"/x/.claude/skills/probatio/references/formal.md\"}},"
                    + "{\"type\":\"tool_use\",\"name\":\"Bash\",\"input\":{\"command\":\"java -cp t.jar tlc2.TLC M.tla\"}}]}}",
                "{\"type\":\"result\",\"subtype\":\"success\",\"total_cost_usd\":1.0,\"num_turns\":6,\"duration_ms\":61000}",
                "{\"type\":\"result\",\"subtype\":\"success\",\"total_cost_usd\":1.5,\"num_turns\":1,\"duration_ms\":2000}") + "\n");
        }
        Files.writeString(out.resolve("run.json"), Json.write(Map.of("fixture", "coupon", "variant", variant, "arm", "skill",
            "skills", List.of("probatio"), "model", "test", "wall_s", 90L, "truth", truth.toString()), true));
        return Eval.grade(out);
    }

    static void expect(Map<String, Object> actual, Map<String, Object> expected) {
        expected.forEach((key, value) -> {
            if (!value.equals(actual.get(key))) {
                throw new AssertionError(key + ": expected " + value + " but got " + actual.get(key) + " in " + actual);
            }
        });
    }
}

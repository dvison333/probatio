import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ground truth for the shop fixture: replays every sequence of shop operations up to a depth, under every
 * pairing of gateway scripts, on the real classes, and checks the README contract after each step.
 *
 * <pre>
 * javac -d out repo/src/*.java check/Explore.java && java -cp out Explore 7     # the defect: exits 1
 * javac -d out repo/src/*.java fixed/src/*.java check/Explore.java ...          # corrected: exits 0
 * </pre>
 */
public class Explore {
    static final long TTL = 1000;
    static final String[] OPS = {"place o1", "place o2", "advance clock", "reaper tick", "retry tick"};
    static final List<List<ScriptedGateway.Step>> SCRIPTS = List.of(
        List.of(),
        List.of(ScriptedGateway.Step.DECLINE),
        List.of(ScriptedGateway.Step.TIMEOUT),
        List.of(ScriptedGateway.Step.CHARGE_THEN_TIMEOUT),
        List.of(ScriptedGateway.Step.TIMEOUT, ScriptedGateway.Step.TIMEOUT),
        List.of(ScriptedGateway.Step.TIMEOUT, ScriptedGateway.Step.DECLINE),
        List.of(ScriptedGateway.Step.TIMEOUT, ScriptedGateway.Step.CHARGE_THEN_TIMEOUT));
    /** {units stocked, units o1 wants, units o2 wants} */
    static final int[][] SHAPES = {{1, 1, 1}, {2, 2, 1}, {2, 1, 1}};

    static final Map<String, Long> violations = new LinkedHashMap<>();
    static final Map<String, String> shortest = new LinkedHashMap<>();
    static long replays;

    public static void main(String[] args) {
        int depth = Integer.parseInt(args[0]);
        for (int[] shape : SHAPES) {
            for (List<ScriptedGateway.Step> first : SCRIPTS) {
                for (List<ScriptedGateway.Step> second : SCRIPTS) {
                    walk(shape, first, second, new ArrayList<>(), depth);
                }
            }
        }
        System.out.println(replays + " sequences replayed to depth " + depth);
        violations.forEach((rule, count) -> System.out.println(rule + ": " + count + " violating sequences, e.g. " + shortest.get(rule)));
        if (violations.isEmpty()) {
            System.out.println("no violation");
        }
        System.exit(violations.isEmpty() ? 0 : 1);
    }

    static void walk(int[] shape, List<ScriptedGateway.Step> first, List<ScriptedGateway.Step> second, List<Integer> ops, int depth) {
        if (!ops.isEmpty()) {
            replays++;
            String broken = replay(shape, first, second, ops);
            if (broken != null) {
                String example = "stock " + shape[0] + ", o1 x" + shape[1] + " " + first + ", o2 x" + shape[2] + " " + second
                    + ": " + ops.stream().map(i -> OPS[i]).toList();
                violations.merge(broken, 1L, Long::sum);
                if (!shortest.containsKey(broken) || ops.size() < depthOf(shortest.get(broken))) {
                    shortest.put(broken, example);
                }
                return; // longer sequences with this prefix add nothing
            }
        }
        if (ops.size() == depth) {
            return;
        }
        for (int op = 0; op < OPS.length; op++) {
            ops.add(op);
            walk(shape, first, second, ops, depth);
            ops.remove(ops.size() - 1);
        }
    }

    static int depthOf(String example) {
        return example.substring(example.lastIndexOf('[')).split(",").length;
    }

    /** Runs the sequence from scratch and names the rule broken after its last step, or returns null. */
    static String replay(int[] shape, List<ScriptedGateway.Step> first, List<ScriptedGateway.Step> second, List<Integer> ops) {
        ManualClock clock = new ManualClock();
        ScriptedGateway gateway = new ScriptedGateway();
        gateway.script("pay-o1", first);
        gateway.script("pay-o2", second);
        Shop shop = new Shop(clock, gateway, TTL);
        shop.stock("book", shape[0]);
        try {
            for (int op : ops) {
                switch (op) {
                    case 0 -> shop.place(new Order("o1", "book", shape[1], 100));
                    case 1 -> shop.place(new Order("o2", "book", shape[2], 100));
                    case 2 -> clock.advance(TTL);
                    case 3 -> shop.reaperTick();
                    default -> shop.retryTick();
                }
            }
        } catch (RuntimeException e) {
            return "an operation threw " + e;
        }
        if (shop.confirmedUnits("book") > shop.stocked("book")) {
            return "1 no overselling";
        }
        if (!shop.anyPending()) {
            for (String id : List.of("o1", "o2")) {
                boolean confirmed = shop.status(id).map(s -> s == OrderStatus.CONFIRMED).orElse(false);
                if (gateway.holdsMoney("pay-" + id) != confirmed) {
                    return "2 no charge without goods";
                }
            }
        }
        if (shop.available("book") + shop.reserved("book") + shop.sold("book") != shop.stocked("book")) {
            return "3 stock accounting";
        }
        if (shop.sold("book") != shop.confirmedUnits("book")) {
            return "(not in the contract) sold units differ from confirmed units";
        }
        return null;
    }
}

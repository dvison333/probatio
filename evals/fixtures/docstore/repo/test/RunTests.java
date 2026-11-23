public class RunTests {
    public static void main(String[] args) {
        DocStore store = new DocStore(new Versions(), new AuditLog());
        check(store.create("notes", "hello") == DocStore.Result.OK, "create succeeds");
        check(store.create("notes", "again") == DocStore.Result.EXISTS, "duplicate create is rejected");

        EditSession ana = new EditSession(store, "notes");
        EditSession bia = new EditSession(store, "notes");
        check(ana.open() && bia.open(), "both sessions open the document");
        ana.type(" from ana");
        bia.type(" from bia");
        check(ana.save() == DocStore.Result.OK, "first save succeeds");
        check(bia.save() == DocStore.Result.CONFLICT, "stale save is rejected");
        check(store.read("notes").orElseThrow().body().equals("hello from ana"), "stale save changed nothing");

        ana.type("!");
        check(ana.save() == DocStore.Result.OK, "a session keeps saving after its own save");
        check(bia.discardDocument() == DocStore.Result.CONFLICT, "stale delete is rejected");
        check(ana.discardDocument() == DocStore.Result.OK, "current delete succeeds");
        check(store.update("notes", 1, "x").result() == DocStore.Result.NOT_FOUND, "update of a missing document");
        System.out.println("all tests passed");
    }

    static void check(boolean condition, String what) {
        if (!condition) {
            throw new AssertionError(what);
        }
    }
}

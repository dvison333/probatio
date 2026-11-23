import java.util.Optional;

/** One editor tab: opens a document, remembers the version it saw, and saves against that version. */
class EditSession {
    private final DocStore store;
    private final String id;
    private long seenVersion;
    private String draft;

    EditSession(DocStore store, String id) {
        this.store = store;
        this.id = id;
    }

    boolean open() {
        Optional<Doc> doc = store.read(id);
        doc.ifPresent(d -> {
            seenVersion = d.version();
            draft = d.body();
        });
        return doc.isPresent();
    }

    void type(String text) {
        draft = draft + text;
    }

    /** Saves the draft; on success the session continues from the new version. */
    DocStore.Result save() {
        DocStore.Result result = store.update(id, seenVersion, draft);
        if (result == DocStore.Result.OK) {
            seenVersion = store.read(id).map(Doc::version).orElse(seenVersion);
        }
        return result;
    }

    DocStore.Result discardDocument() {
        return store.delete(id, seenVersion);
    }
}

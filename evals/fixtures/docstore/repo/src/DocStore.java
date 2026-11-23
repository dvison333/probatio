import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class DocStore {
    enum Result { OK, EXISTS, NOT_FOUND, CONFLICT }

    /** The outcome of an update and, when it is OK, the version the document now has. */
    record Saved(Result result, long version) {}

    private final Map<String, Doc> docs = new ConcurrentHashMap<>();
    private final Versions versions;
    private final AuditLog audit;

    DocStore(Versions versions, AuditLog audit) {
        this.versions = versions;
        this.audit = audit;
    }

    Optional<Doc> read(String id) {
        return Optional.ofNullable(docs.get(id));
    }

    Result create(String id, String body) {
        Doc created = new Doc(body, versions.first());
        if (docs.putIfAbsent(id, created) != null) {
            return Result.EXISTS;
        }
        audit.record("create", id, created.version());
        return Result.OK;
    }

    Saved update(String id, long expectedVersion, String body) {
        Saved[] saved = {new Saved(Result.NOT_FOUND, expectedVersion)};
        docs.computeIfPresent(id, (key, current) -> {
            if (current.version() != expectedVersion) {
                saved[0] = new Saved(Result.CONFLICT, expectedVersion);
                return current;
            }
            Doc updated = new Doc(body, versions.next(current.version()));
            saved[0] = new Saved(Result.OK, updated.version());
            audit.record("update", id, updated.version());
            return updated;
        });
        return saved[0];
    }

    Result delete(String id, long expectedVersion) {
        Result[] result = {Result.NOT_FOUND};
        docs.computeIfPresent(id, (key, current) -> {
            if (current.version() != expectedVersion) {
                result[0] = Result.CONFLICT;
                return current;
            }
            result[0] = Result.OK;
            audit.record("delete", id, current.version());
            return null;
        });
        return result[0];
    }
}

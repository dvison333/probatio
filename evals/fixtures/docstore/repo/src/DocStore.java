import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

class DocStore {
    enum Result { OK, EXISTS, NOT_FOUND, CONFLICT }

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

    Result update(String id, long expectedVersion, String body) {
        Result[] result = {Result.NOT_FOUND};
        docs.computeIfPresent(id, (key, current) -> {
            if (current.version() != expectedVersion) {
                result[0] = Result.CONFLICT;
                return current;
            }
            result[0] = Result.OK;
            Doc updated = new Doc(body, versions.next(current.version()));
            audit.record("update", id, updated.version());
            return updated;
        });
        return result[0];
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

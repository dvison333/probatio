# Document store

Stores text documents for a collaborative editor. Several editor sessions work on the same documents at once.

## Contract

- Optimistic locking: `update` and `delete` take the version the caller read. They succeed only if the document is unchanged since that read; otherwise they return `CONFLICT` and change nothing.
- A caller therefore never overwrites or deletes content it has not seen.
- Each store operation is atomic.

## Tests

```sh
sh test.sh
```

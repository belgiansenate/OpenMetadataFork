# Every by-query write names a one-minute scroll keep-alive

- **Status:** Accepted
- **Revisions:** v1 2026-11-04 (initial)
- **Deciders:** mc
- **Guard:** `OpenSearchEntityManagerScrollKeepAliveTest`, `ElasticSearchEntityManagerScrollKeepAliveTest`
- **Related:** ADR:2026-11-03-a-failed-background-propagation-is-replayed (the failure this limits)

## Context

Update-by-query, delete-by-query and reindex run on a scroll: the engine opens one context per
shard and clears them when the task finishes. That clear is a separate action,
`indices:data/read/scroll/clear`, which OpenSearch Security treats as cluster-level because a
scroll id names no index. On a shared cluster whose administrators grant OpenMetadata's user index
permissions only, the clear is refused; the engine logs it and carries on, and every context lives
until its keep-alive expires. OpenMetadata never set that keep-alive, so the engine default of
5 minutes applied (plus up to one `search.keep_alive_interval` before the reaper removes it).

Measured on such a cluster (3 nodes, `search.max_open_scroll_context` 1000): a sequential load
that adds one lineage edge per second holds ~300 contexts; a delete fans out over every index and
holds one context per index; a delete followed by a load reached the limit on every node and the
load's writes were rejected with `too_many_scroll_contexts_exception`. Reproduced locally on
OpenSearch 2.19.1 with the security plugin: a role without the clear permission leaks one context
per write for the full keep-alive, the admin role leaks none.

## Decision

- Every update-by-query, delete-by-query and reindex request in both entity managers sets
  `scroll` to `SearchPropagationLimits.BY_QUERY_SCROLL_KEEP_ALIVE`, which is `1m`. A new
  by-query site sets it too; the guard tests cover one site of each shape (request object, inline
  builder, delete, reindex, background propagation).
- The value is a constant, not configuration: a keep-alive only has to outlive the processing of
  one batch, which the engine renews on every batch and extends by the throttle delay, so one
  value serves every deployment.
- OpenMetadata does not clear scroll contexts itself: a by-query response carries no scroll id, and
  clearing needs the very permission a restricted role lacks.

## Consequences

A context the engine could not clear now expires after 1–2 minutes instead of 5–6, so the same
load holds roughly a fifth of the contexts and a delete settles in two minutes. A single batch that
takes longer than a minute to bulk-index fails its task with `search_context_missing`; the
reconciler retries a synchronous write and the propagation check replays a background one. Revisit
the value if such failures appear, or if the engine ever lets a by-query clear its scroll without
the cluster-level permission.

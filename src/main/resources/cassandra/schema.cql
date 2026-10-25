-- =============================================================================
-- Friendly Eats · Apache Cassandra (escrita massiva)
-- Logs de atividade e notificações. A timeline do feed (fan-out on write) entra com o RF de feed.
-- =============================================================================

CREATE KEYSPACE IF NOT EXISTS friendly_eats
    WITH replication = {'class': 'NetworkTopologyStrategy', 'datacenter1': 3}
    AND durable_writes = true;

CREATE TABLE IF NOT EXISTS friendly_eats.activity_log (
    user_id       bigint,
    day_bucket    date,
    event_id      timeuuid,
    action        text,           -- LOGIN, REVIEW_PUBLISHED, CONSENT_REVOKED, DATA_EXPORTED ...
    target_id     text,
    metadata      map<text, text>,
    PRIMARY KEY ((user_id, day_bucket), event_id)
) WITH CLUSTERING ORDER BY (event_id DESC);

CREATE TABLE IF NOT EXISTS friendly_eats.notification (
    user_id         bigint,
    notification_id timeuuid,
    kind            text,         -- LIKE, COMMENT, SHARE, FOOD_ALERT ...
    payload         text,         -- JSON
    read            boolean,
    PRIMARY KEY ((user_id), notification_id)
) WITH CLUSTERING ORDER BY (notification_id DESC)
  AND default_time_to_live = 2592000;  -- 30 dias

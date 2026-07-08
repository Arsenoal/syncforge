-- SyncForge Supabase schema + RPC (mirrors :syncforge-server SyncStore / JdbcSyncStore).
-- Apply via Supabase SQL editor or `supabase db push`.

CREATE TABLE IF NOT EXISTS sync_entity (
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    payload_json TEXT,
    server_version BIGINT NOT NULL,
    updated_at_millis BIGINT NOT NULL,
    is_deleted BOOLEAN NOT NULL,
    PRIMARY KEY (entity_type, entity_id)
);

CREATE TABLE IF NOT EXISTS sync_version_counter (
    id INT PRIMARY KEY CHECK (id = 1),
    next_version BIGINT NOT NULL
);

INSERT INTO sync_version_counter (id, next_version)
VALUES (1, 1)
ON CONFLICT (id) DO NOTHING;

CREATE OR REPLACE FUNCTION syncforge_allocate_server_version()
RETURNS BIGINT
LANGUAGE plpgsql
AS $$
DECLARE
    next_version BIGINT;
BEGIN
    UPDATE sync_version_counter
    SET next_version = next_version + 1
    WHERE id = 1
    RETURNING next_version INTO next_version;

    IF next_version IS NULL THEN
        RAISE EXCEPTION 'sync_version_counter row missing';
    END IF;

    RETURN next_version;
END;
$$;

CREATE OR REPLACE FUNCTION syncforge_push(entries jsonb, now_millis bigint)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    entry jsonb;
    acknowledged jsonb := '[]'::jsonb;
    rejected jsonb := '[]'::jsonb;
    v_entity_type text;
    v_entity_id text;
    v_change_type text;
    v_payload_json text;
    v_local_version bigint;
    v_outbox_id bigint;
    v_existing_version bigint;
    v_existing_deleted boolean;
    v_found boolean;
    v_next_version bigint;
BEGIN
    FOR entry IN SELECT value FROM jsonb_array_elements(entries) AS t(value)
    LOOP
        v_outbox_id := (entry->>'id')::bigint;
        v_entity_type := entry->>'entityType';
        v_entity_id := entry->>'entityId';
        v_change_type := entry->>'changeType';
        v_payload_json := entry->>'payloadJson';
        v_local_version := COALESCE((entry->>'localVersion')::bigint, 0);

        IF v_entity_type IS NULL OR v_entity_type = '' OR v_entity_id IS NULL OR v_entity_id = '' THEN
            rejected := rejected || jsonb_build_array(
                jsonb_build_object(
                    'outboxId', v_outbox_id,
                    'code', 'VALIDATION',
                    'message', 'entityType and entityId are required'
                )
            );
            CONTINUE;
        END IF;

        SELECT server_version, is_deleted
        INTO v_existing_version, v_existing_deleted
        FROM sync_entity
        WHERE entity_type = v_entity_type
          AND entity_id = v_entity_id;

        v_found := FOUND;

        IF v_found AND v_existing_deleted AND v_change_type IN ('CREATE', 'UPDATE') THEN
            rejected := rejected || jsonb_build_array(
                jsonb_build_object(
                    'outboxId', v_outbox_id,
                    'code', 'CONFLICT',
                    'message', 'Entity was deleted on the server'
                )
            );
            CONTINUE;
        END IF;

        IF v_change_type = 'UPDATE' AND v_found AND v_existing_version <> v_local_version - 1 THEN
            rejected := rejected || jsonb_build_array(
                jsonb_build_object(
                    'outboxId', v_outbox_id,
                    'code', 'CONFLICT',
                    'message', format(
                        'Server version %s does not match expected base %s',
                        v_existing_version,
                        v_local_version - 1
                    )
                )
            );
            CONTINUE;
        END IF;

        v_next_version := syncforge_allocate_server_version();

        IF v_change_type = 'DELETE' THEN
            INSERT INTO sync_entity (entity_type, entity_id, payload_json, server_version, updated_at_millis, is_deleted)
            VALUES (v_entity_type, v_entity_id, NULL, v_next_version, now_millis, TRUE)
            ON CONFLICT (entity_type, entity_id) DO UPDATE
            SET payload_json = EXCLUDED.payload_json,
                server_version = EXCLUDED.server_version,
                updated_at_millis = EXCLUDED.updated_at_millis,
                is_deleted = EXCLUDED.is_deleted;
        ELSE
            INSERT INTO sync_entity (entity_type, entity_id, payload_json, server_version, updated_at_millis, is_deleted)
            VALUES (v_entity_type, v_entity_id, v_payload_json, v_next_version, now_millis, FALSE)
            ON CONFLICT (entity_type, entity_id) DO UPDATE
            SET payload_json = EXCLUDED.payload_json,
                server_version = EXCLUDED.server_version,
                updated_at_millis = EXCLUDED.updated_at_millis,
                is_deleted = EXCLUDED.is_deleted;
        END IF;

        acknowledged := acknowledged || to_jsonb(v_outbox_id);
    END LOOP;

    RETURN jsonb_build_object(
        'acknowledgedIds', acknowledged,
        'rejected', rejected
    );
END;
$$;

CREATE OR REPLACE FUNCTION syncforge_pull(
    since_millis bigint,
    entity_types text[] DEFAULT NULL,
    page_limit integer DEFAULT 2147483647,
    page_cursor text DEFAULT NULL,
    now_millis bigint DEFAULT (extract(epoch FROM clock_timestamp()) * 1000)::bigint
)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    all_deltas jsonb;
    start_index integer := 0;
    end_index integer;
    page jsonb;
    total integer;
    decoded text;
BEGIN
    IF page_cursor IS NOT NULL AND btrim(page_cursor) <> '' THEN
        decoded := convert_from(decode(page_cursor, 'base64'), 'UTF8');
        start_index := COALESCE(decoded::integer, 0);
    END IF;

    SELECT COALESCE(
        jsonb_agg(
            jsonb_build_object(
                'entityType', entity_type,
                'entityId', entity_id,
                'payloadJson', payload_json,
                'serverVersion', server_version,
                'updatedAtMillis', updated_at_millis,
                'isDeleted', is_deleted
            )
            ORDER BY updated_at_millis ASC
        ),
        '[]'::jsonb
    )
    INTO all_deltas
    FROM sync_entity
    WHERE updated_at_millis > since_millis
      AND (
          entity_types IS NULL
          OR cardinality(entity_types) = 0
          OR entity_type = ANY (entity_types)
      );

    total := jsonb_array_length(all_deltas);
    start_index := GREATEST(LEAST(start_index, total), 0);
    end_index := LEAST(start_index + page_limit, total);

    IF start_index >= total THEN
        page := '[]'::jsonb;
    ELSE
        page := (
            SELECT COALESCE(jsonb_agg(element), '[]'::jsonb)
            FROM (
                SELECT all_deltas -> idx AS element
                FROM generate_series(start_index, end_index - 1) AS s(idx)
            ) sliced
        );
    END IF;

    RETURN jsonb_build_object(
        'deltas', page,
        'serverTimestampMillis', now_millis,
        'hasMore', end_index < total,
        'nextPageCursor', CASE
            WHEN end_index < total THEN encode(convert_to(end_index::text, 'UTF8'), 'base64')
            ELSE NULL
        END
    );
END;
$$;

GRANT EXECUTE ON FUNCTION syncforge_push(jsonb, bigint) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION syncforge_pull(bigint, text[], integer, text, bigint) TO authenticated, service_role;

ALTER TABLE sync_entity ENABLE ROW LEVEL SECURITY;
ALTER TABLE sync_version_counter ENABLE ROW LEVEL SECURITY;

-- Realtime: add sync_entity to supabase_realtime when the publication exists.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
        ALTER PUBLICATION supabase_realtime ADD TABLE sync_entity;
    END IF;
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;
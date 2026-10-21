CREATE INDEX idx_tourism_contents_new_event_lookup
    ON tourism_contents (
        classification_code_1,
        status,
        deleted_at,
        source_created_at,
        id
    );

DROP TABLE event_detail_items;

ALTER TABLE tourism_contents
    DROP INDEX ix_tourism_contents_detail_bootstrap,
    DROP COLUMN detail_synced_at;

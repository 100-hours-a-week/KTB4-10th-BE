ALTER TABLE regions
    ADD COLUMN parent_id BIGINT NULL AFTER id,
    ADD COLUMN region_level VARCHAR(20) NOT NULL DEFAULT 'PROVINCE'
        AFTER name,
    ADD CONSTRAINT fk_regions_parent
        FOREIGN KEY (parent_id) REFERENCES regions (id),
    ADD CONSTRAINT ck_regions_hierarchy
        CHECK (
            (region_level = 'PROVINCE' AND parent_id IS NULL)
            OR (region_level = 'DISTRICT' AND parent_id IS NOT NULL)
        ),
    ADD INDEX ix_regions_parent_name (parent_id, name);

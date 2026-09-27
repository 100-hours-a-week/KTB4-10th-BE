ALTER TABLE tourism_contents
    DROP INDEX ix_tourism_contents_region_category,
    DROP COLUMN category,
    ADD INDEX ix_tourism_contents_region_classification_1
        (region_id, classification_code_1);

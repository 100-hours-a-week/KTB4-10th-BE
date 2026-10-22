CREATE INDEX ix_favorite_contents_member_created
    ON favorite_contents (member_id, created_at DESC, id DESC);

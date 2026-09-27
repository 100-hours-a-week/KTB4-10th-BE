SELECT 'regions' AS table_name, COUNT(*) AS row_count
FROM regions
UNION ALL
SELECT 'tourism_contents', COUNT(*)
FROM tourism_contents
UNION ALL
SELECT 'event_details', COUNT(*)
FROM event_details;

SELECT COUNT(*) AS contents_without_region
FROM tourism_contents AS content
LEFT JOIN regions AS region ON region.id = content.region_id
WHERE region.id IS NULL;

SELECT COUNT(*) AS events_without_content
FROM event_details AS event
LEFT JOIN tourism_contents AS content ON content.id = event.content_id
WHERE content.id IS NULL;

SELECT classification_code_1, COUNT(*) AS row_count
FROM tourism_contents
GROUP BY classification_code_1
ORDER BY classification_code_1;


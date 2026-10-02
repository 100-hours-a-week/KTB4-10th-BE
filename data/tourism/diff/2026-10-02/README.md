# TourAPI areaBasedList2 snapshot diff

- Old snapshot: `data/tourism/raw/2026-10-01/areaBasedList2.json`
- New snapshot: `data/tourism/raw/2026-10-02/areaBasedList2.json`
- Old count: 49623
- New count: 49628
- Added: 19
- Removed from current list: 14
- Modified (`modifiedtime` changed): 251

`removed` means that the content ID is absent from the new complete snapshot. It does
not by itself prove permanent deletion, so do not physically delete referenced content.

## Files

- `summary.csv`: counts by every `lclsSystm1` classification
- `added.tsv`: newly observed content IDs
- `removed.tsv`: IDs absent from the new snapshot
- `modified.tsv`: common IDs whose `modifiedtime` changed

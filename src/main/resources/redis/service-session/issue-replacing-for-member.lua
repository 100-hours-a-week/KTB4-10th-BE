-- KEYS: new session, member index. ARGV: prefix, hash, session ID, member ID, status, absolute ms, idle ms.
-- Standalone Redis only: previous-session keys are resolved from the member index inside this script.
local function validId(value)
    return value and string.match(value, '^[1-9]%d*$') and
        (#value < 19 or (#value == 19 and value <= '9223372036854775807'))
end
local previous = redis.call('GET', KEYS[2])
local oldKey = nil
local oldId = ''
if previous then
    local oldHash
    oldHash, oldId = string.match(previous, '^([0-9a-f]+):([1-9]%d*)$')
    assert(oldHash and #oldHash == 64 and validId(oldId), 'Invalid session index')
    oldKey = ARGV[1] .. oldHash
    assert(oldHash ~= ARGV[2] and oldId ~= ARGV[3], 'Session token and ID must be fresh')
    if redis.call('EXISTS', oldKey) == 1 then
        local owner = redis.call('HMGET', oldKey, 'schemaVersion', 'memberId', 'sessionId')
        assert(owner[1] == '1' and owner[2] == ARGV[4] and owner[3] == oldId, 'Session index ownership mismatch')
    end
end
-- Reject collisions before deleting the old login. Issuance is not a retry/upsert operation.
assert(redis.call('EXISTS', KEYS[1]) == 0, 'Session key already exists')
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local absolute = tonumber(ARGV[6])
local idle = tonumber(ARGV[7])
local expires = now + absolute
assert(expires <= 9007199254740991, 'Session expiry exceeds supported range')
local createdText = string.format('%.0f', now)
local expiresText = string.format('%.0f', expires)

-- All record/type validation precedes writes; Redis Lua does not roll back writes on script errors.
redis.call('HSET', KEYS[1], 'schemaVersion', '1', 'sessionId', ARGV[3], 'memberId', ARGV[4],
    'memberStatus', ARGV[5], 'createdAtEpochMillis', createdText, 'expiresAtEpochMillis', expiresText,
    'lastUsedAtEpochMillis', createdText, 'revoked', '0', 'memberDeleted', '0')
redis.call('PEXPIREAT', KEYS[1], now + math.min(idle, absolute))
redis.call('SET', KEYS[2], ARGV[2] .. ':' .. ARGV[3], 'PXAT', expiresText)
if oldKey then redis.call('DEL', oldKey) end
return createdText .. ':' .. expiresText .. ':' .. oldId

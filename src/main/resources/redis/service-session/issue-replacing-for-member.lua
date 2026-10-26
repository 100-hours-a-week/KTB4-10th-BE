-- Standalone Redis. KEYS: new session, member index. ARGV: prefix, hash, member ID, absolute ms, idle ms.
local previous = redis.call('GET', KEYS[2])
if previous then
    assert(#previous == 64 and string.match(previous, '^[0-9a-f]+$'), 'Invalid session index')
    assert(previous ~= ARGV[2], 'Session token must be fresh')
    local oldKey = ARGV[1] .. previous
    if redis.call('EXISTS', oldKey) == 1 then
        local owner = redis.call('HMGET', oldKey, 'schemaVersion', 'memberId')
        assert(owner[1] == '2' and owner[2] == ARGV[3], 'Session index ownership mismatch')
    end
end
assert(redis.call('EXISTS', KEYS[1]) == 0, 'Session key already exists')
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
local absolute, idle = tonumber(ARGV[4]), tonumber(ARGV[5])
local expires = now + absolute
assert(expires <= 9007199254740991, 'Session expiry exceeds supported range')
local createdText, expiresText = string.format('%.0f', now), string.format('%.0f', expires)
redis.call('HSET', KEYS[1], 'schemaVersion', '2', 'memberId', ARGV[3],
    'createdAtEpochMillis', createdText, 'expiresAtEpochMillis', expiresText,
    'lastUsedAtEpochMillis', createdText)
redis.call('PEXPIREAT', KEYS[1], now + math.min(idle, absolute))
redis.call('SET', KEYS[2], ARGV[2], 'PXAT', expiresText)
if previous then redis.call('DEL', ARGV[1] .. previous) end
return createdText .. ':' .. expiresText .. ':' .. (previous or '')

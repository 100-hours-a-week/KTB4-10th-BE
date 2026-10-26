if redis.call('EXISTS', KEYS[1]) == 0 then return '' end
local fields = redis.call('HMGET', KEYS[1], 'schemaVersion', 'memberId',
    'createdAtEpochMillis', 'expiresAtEpochMillis', 'lastUsedAtEpochMillis')
local function timestamp(value)
    assert(value and string.match(value, '^%d+$'), 'Invalid session timestamp')
    local n = tonumber(value)
    assert(n and n >= 0 and n <= 9007199254740991, 'Invalid session timestamp')
    return n
end
assert(fields[1] == '2', 'Unsupported session schema')
local id = fields[2]
assert(id and string.match(id, '^[1-9]%d*$') and
    (#id < 19 or (#id == 19 and id <= '9223372036854775807')), 'Invalid member ID')
local created = timestamp(fields[3])
local expires = timestamp(fields[4])
local lastUsed = fields[5] and timestamp(fields[5]) or created
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
assert(created <= lastUsed and lastUsed <= now and created < expires, 'Invalid session timeline')
local idle = tonumber(ARGV[1])
if now >= expires or now - lastUsed >= idle then return '' end
redis.call('HSET', KEYS[1], 'lastUsedAtEpochMillis', string.format('%.0f', now))
redis.call('PEXPIRE', KEYS[1], math.min(idle, expires - now))
return id

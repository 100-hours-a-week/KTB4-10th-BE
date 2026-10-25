-- Validate everything before mutation. Corrupt records are backend errors, not absent sessions.
if redis.call('EXISTS', KEYS[1]) == 0 then return '' end
local fields = redis.call('HMGET', KEYS[1], 'schemaVersion', 'sessionId', 'memberId',
    'memberStatus', 'createdAtEpochMillis', 'expiresAtEpochMillis', 'lastUsedAtEpochMillis',
    'revoked', 'memberDeleted')
local function requireValid(condition)
    if not condition then error('Invalid Redis service session record') end
end
local function validId(value)
    return value and string.match(value, '^[1-9]%d*$') and
        (#value < 19 or (#value == 19 and value <= '9223372036854775807'))
end
local function timestamp(value)
    requireValid(value and string.match(value, '^%d+$'))
    local number = tonumber(value)
    requireValid(number and number >= 0 and number <= 9007199254740991)
    return number
end
requireValid(fields[1] == '1' and validId(fields[2]) and validId(fields[3]))
requireValid(fields[4] == 'ACTIVE' or fields[4] == 'ONBOARDING')
requireValid(fields[8] == '0' or fields[8] == '1')
requireValid(fields[9] == '0' or fields[9] == '1')
local created = timestamp(fields[5])
local expires = timestamp(fields[6])
local lastUsed = created
if fields[7] then lastUsed = timestamp(fields[7]) end
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
requireValid(created <= lastUsed and lastUsed <= now and created < expires)
local idle = tonumber(ARGV[1])
if fields[8] == '1' or fields[9] == '1' or now >= expires or now - lastUsed >= idle then
    -- Push lifecycle is deliberately not changed by authentication or natural expiry.
    return ''
end
redis.call('HSET', KEYS[1], 'lastUsedAtEpochMillis', string.format('%.0f', now))
redis.call('PEXPIRE', KEYS[1], math.min(idle, expires - now))
return fields[2] .. ':' .. fields[3] .. ':' .. fields[4]

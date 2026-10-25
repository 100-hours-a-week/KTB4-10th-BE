-- KEYS: member index. ARGV: session prefix, member ID, optional target hash and session ID.
local function validId(value)
    return value and string.match(value, '^[1-9]%d*$') and
        (#value < 19 or (#value == 19 and value <= '9223372036854775807'))
end
local pointer = redis.call('GET', KEYS[1])
local indexedHash = nil
local indexedId = nil
if pointer then
    indexedHash, indexedId = string.match(pointer, '^([0-9a-f]+):([1-9]%d*)$')
    assert(indexedHash and #indexedHash == 64 and validId(indexedId), 'Invalid session index')
end
if not pointer and not ARGV[3] then return '' end

-- Logout specifies its target; member revocation uses the indexed session.
local hash = ARGV[3] or indexedHash
local id = ARGV[4] or indexedId
local key = ARGV[1] .. hash
local ownsIndex = pointer == hash .. ':' .. id
local exists = redis.call('EXISTS', key) == 1
if exists then
    local owner = redis.call('HMGET', key, 'schemaVersion', 'memberId', 'sessionId')
    assert(owner[1] == '1' and validId(owner[2]) and validId(owner[3]), 'Invalid session record')
    if owner[2] ~= ARGV[2] or owner[3] ~= id then
        assert(ARGV[3], 'Session index ownership mismatch')
        return ''
    end
end
if not exists and not ownsIndex then return '' end
redis.call('DEL', key)
if ownsIndex then redis.call('DEL', KEYS[1]) end
return id

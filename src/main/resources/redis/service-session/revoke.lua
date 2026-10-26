-- KEYS: member index. ARGV: session prefix, member ID, optional explicit target hash.
local pointer = redis.call('GET', KEYS[1])
if pointer then
    assert(#pointer == 64 and string.match(pointer, '^[0-9a-f]+$'), 'Invalid session index')
end
local hash = ARGV[3] or pointer
if not hash then return '' end
local key = ARGV[1] .. hash
local ownsIndex = pointer == hash
local exists = redis.call('EXISTS', key) == 1
if exists then
    local owner = redis.call('HMGET', key, 'schemaVersion', 'memberId')
    assert(owner[1] == '2' and owner[2], 'Invalid session record')
    if owner[2] ~= ARGV[2] then
        assert(ARGV[3], 'Session index ownership mismatch')
        return ''
    end
end
if not exists and not ownsIndex then return '' end
redis.call('DEL', key)
if ownsIndex then redis.call('DEL', KEYS[1]) end
return hash

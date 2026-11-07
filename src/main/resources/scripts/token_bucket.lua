-- KEYS[1] = bucket key
-- ARGV[1] = bucket capacity
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = request cost

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local cost = tonumber(ARGV[3])

-- Redis server time, not application-server time — keeps replicas in agreement.
local redisTime = redis.call('TIME')
local now = tonumber(redisTime[1]) * 1000 + math.floor(tonumber(redisTime[2]) / 1000)

local tokens = tonumber(redis.call('HGET', key, 'tokens'))        -- false on miss -> tonumber(false) = nil
local lastRefill = tonumber(redis.call('HGET', key, 'lastRefill'))

if tokens == nil then tokens = capacity end         -- new bucket starts full
if lastRefill == nil then lastRefill = now end

local elapsed = math.max(0, now - lastRefill)
tokens = math.min(capacity, tokens + (elapsed / 1000.0) * refillRate)

local allowed = 0
local retryAfter = 0
if tokens >= cost then
    tokens = tokens - cost
    allowed = 1
else
    retryAfter = math.ceil((cost - tokens) / refillRate)
end

local resetAfter = math.ceil((capacity - tokens) / refillRate)
local resetAt = math.ceil(now / 1000) + resetAfter

-- Both fields explicitly formatted now, not one formatted and one raw.
redis.call('HSET', key, 'tokens', string.format('%.6f', tokens), 'lastRefill', string.format('%.0f', now))

-- Expire inactive buckets so abandoned keys don't accumulate forever.
local ttl = math.ceil((capacity / refillRate) * 2000)
redis.call('PEXPIRE', key, math.max(ttl, 1000))

return { allowed, math.floor(tokens), retryAfter, resetAt }

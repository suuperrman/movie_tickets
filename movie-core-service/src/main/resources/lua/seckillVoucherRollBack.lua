-- 秒杀库存回滚（MQ 发送失败时补偿）
-- KEYS[1] 库存 key   seckill:stock:{voucherId}
-- KEYS[2] 已购用户集合 key  seckill:user:{voucherId}
-- ARGV[1] 用户id
-- ARGV[2] 回滚数量

local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]
local qty = tonumber(ARGV[2])

redis.call('INCRBY', stockKey, qty)
redis.call('SREM', userKey, userId)

return 1

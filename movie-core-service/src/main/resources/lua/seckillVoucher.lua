-- 秒杀预扣库存（原子操作）
-- KEYS[1] 库存 key   seckill:stock:{voucherId}
-- KEYS[2] 已购用户集合 key  seckill:user:{voucherId}
-- ARGV[1] 用户id
-- ARGV[2] 扣减数量
-- 返回 JSON：code 0=成功 1=库存不足 2=重复购买 3=库存未初始化

local stockKey = KEYS[1]
local userKey = KEYS[2]
local userId = ARGV[1]
local qty = tonumber(ARGV[2])

if redis.call('SISMEMBER', userKey, userId) == 1 then
    return cjson.encode({code = 2, beforeQty = -1, deductQty = 0, afterQty = -1})
end

local stockStr = redis.call('GET', stockKey)
if stockStr == false or stockStr == nil then
    return cjson.encode({code = 3, beforeQty = -1, deductQty = 0, afterQty = -1})
end

local stock = tonumber(stockStr)
if stock < qty then
    return cjson.encode({code = 1, beforeQty = stock, deductQty = 0, afterQty = stock})
end

local afterQty = redis.call('DECRBY', stockKey, qty)
redis.call('SADD', userKey, userId)

return cjson.encode({code = 0, beforeQty = stock, deductQty = qty, afterQty = afterQty})

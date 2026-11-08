-- OpenOS: библиотека uuid
local uuid = {}
function uuid.next()
  local s = ""
  for i = 1, 16 do
    local b = math.random(0, 255)
    if i == 7 then b = b % 16 + 64 elseif i == 9 then b = b % 64 + 128 end
    s = s .. string.format("%02x", b)
    if i == 4 or i == 6 or i == 8 or i == 10 then s = s .. "-" end
  end
  return s
end
return uuid

-- OpenOS: библиотека serialization
local serialization = {}
local keywords = {}
for k in ("and break do else elseif end false for function goto if in local nil not or repeat return then true until while"):gmatch("%a+") do keywords[k] = true end

local function ser(v, pretty, indent, seen)
  local t = type(v)
  if t == "string" then return string.format("%q", v):gsub("\\\n", "\\n")
  elseif t == "number" then
    if v ~= v then return "0/0" elseif v == math.huge then return "math.huge" elseif v == -math.huge then return "-math.huge" end
    return tostring(v)
  elseif t == "boolean" or t == "nil" then return tostring(v)
  elseif t == "table" then
    if seen[v] then error("tables with cycles are not supported") end
    seen[v] = true
    local parts, i = {}, 1
    for k, val in pairs(v) do
      local key
      if k == i then key = nil; i = i + 1
      elseif type(k) == "string" and k:match("^[%a_][%w_]*$") and not keywords[k] then key = k
      else key = "[" .. ser(k, false, "", seen) .. "]" end
      local item = ser(val, pretty, indent .. "  ", seen)
      parts[#parts + 1] = key and (key .. "=" .. item) or item
    end
    seen[v] = nil
    if pretty then return "{\n" .. indent .. "  " .. table.concat(parts, ",\n" .. indent .. "  ") .. "\n" .. indent .. "}" end
    return "{" .. table.concat(parts, ",") .. "}"
  end
  error("cannot serialize a " .. t)
end

function serialization.serialize(value, pretty)
  return ser(value, pretty, "", {})
end

function serialization.unserialize(data)
  local fn, err = load("return " .. tostring(data), "=data", "t", { math = { huge = math.huge } })
  if not fn then return nil, err end
  local ok, res = pcall(fn)
  if not ok then return nil, res end
  return res
end

return serialization

-- Интерактивный Lua: введите выражение или команду, exit() — выход.
local env = setmetatable({}, { __index = _G })
local running = true
env.exit = function() running = false end
local history = {}
if term.isColor() then term.setTextColor(colors.lightGray) end
print("Интерактивный Lua. exit() — выход.")
term.setTextColor(colors.white)
while running do
  if term.isColor() then term.setTextColor(colors.yellow) end
  write("lua> ")
  term.setTextColor(colors.white)
  local line = read(nil, history)
  if line:match("%S") then
    history[#history + 1] = line
    local fn, err = load("return " .. line, "=lua", "t", env)
    if not fn then fn, err = load(line, "=lua", "t", env) end
    if not fn then
      printError(err)
    else
      local res = table.pack(pcall(fn))
      if not res[1] then
        printError(res[2])
      elseif res.n > 1 then
        local out = {}
        for i = 2, res.n do
          local v = res[i]
          out[#out + 1] = type(v) == "table" and textutils.serialize(v) or tostring(v)
        end
        print(table.concat(out, "\t"))
      end
    end
  end
end

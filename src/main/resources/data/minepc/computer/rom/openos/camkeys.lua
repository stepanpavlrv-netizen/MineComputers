-- Пароли камер, запомненные на этом компьютере: файл /home/.cameras («адрес<Tab>пароль» в строке).
-- Им пользуются команда cam и программа «Камеры» в Wired.
--   local K = require("camkeys")
--   K.login(имя, пароль, запомнить) → сведения о камере или nil, ошибка («Неверный пароль», «Слишком часто…»)
--   K.auto(камера)  — камера из camera.list(): войти сохранённым паролем, если она закрыта; true — можно смотреть
--   K.forget(адрес) — забыть пароль камеры
-- Пароль в файле лежит открыто: кто доберётся до компьютера, тот и увидит камеры (поэтому «запомнить» можно снять).
local K = { FILE = "/home/.cameras" }

local function load()
  local out = {}
  local f = fs.exists(K.FILE) and fs.open(K.FILE, "r")
  if f then
    for line in (f.readAll() or ""):gmatch("[^\n]+") do
      local a, p = line:match("^([^\t]+)\t([^\r]*)")      -- \r — если файл правили в Windows
      if a then out[a] = p end
    end
    f.close()
  end
  return out
end

local function save(t)
  local lines = {}
  for a, p in pairs(t) do lines[#lines + 1] = a .. "\t" .. p end
  if #lines == 0 then
    if fs.exists(K.FILE) then fs.delete(K.FILE) end
    return
  end
  if not fs.exists("/home") then fs.makeDir("/home") end
  local f = fs.open(K.FILE, "w")
  if f then f.write(table.concat(lines, "\n") .. "\n") f.close() end
end

function K.saved(address) return load()[address] end

function K.forget(address)
  local t = load()
  if t[address] then t[address] = nil save(t) end
end

function K.login(name, password, remember)
  local r, err = camera.login(name, password)
  if not r then return nil, err end
  local t = load()
  if remember then t[r.address] = password else t[r.address] = nil end
  save(t)
  return r
end

function K.auto(c)
  if not c.locked then return true end
  local p = load()[c.address]
  if not p then return false end
  local r, err = camera.login(c.address, p)
  if r then return true end
  -- пароль сменили: забыть старый, чтобы не стучаться с ним снова и не засорять журнал камеры
  if err == "Неверный пароль" then K.forget(c.address) end
  return false, err
end

return K

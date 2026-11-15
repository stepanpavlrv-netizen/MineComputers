-- Хостинг: сайты на сервере (служба hostd). Клиент — эта библиотека: deploy, Сайтостроитель.
--   local hosting = require("hosting")
--   local s = hosting.connect("srv-1", "вася", "пароль")       -- сервер: имя, адрес или имя .mine
--   s:info()                     → { user, quota, used, sites = { {name, size, files} } } или nil, ошибка
--   s:upload("/www", "shop", onProgress)  → адрес сайта или nil, ошибка (загружает только изменённые файлы)
--   s:remove("shop.mine")
--   hosting.discover()           → серверы с хостингом в сети: { {address, name, sites, free} }
-- quiet = true в hosting.connect(..., {quiet = true}) — не возвращать чужие события в очередь (для Wired).
local hosting = { PORT = 2121, CHUNK = 6000 }

local function hex(s) return data.toHex(data.sha256(s)) end
hosting.hash = hex

--- Ждать ответ HOSTOK с номером id; чужие события (если не quiet) вернуть в очередь.
local function wait(port, id, timeout, quiet)
  local timer = os.startTimer(timeout or 5)
  local skipped, res = {}, nil
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "HOSTOK" and ev[6] == id then
      res = { ok = ev[7], value = ev[8] }
      break
    elseif ev[1] == "timer" and ev[2] == timer then
      break
    elseif ev[1] == "terminate" then
      res = { ok = false, value = "Прервано" }
      break
    elseif not quiet then
      skipped[#skipped + 1] = ev
    end
  end
  os.cancelTimer(timer)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  return res
end

local Session = {}
Session.__index = Session

--- Запрос к серверу: ok, значение или nil, ошибка.
function Session:call(cmd, ...)
  if net.speed() == 0 then return nil, "Нет сети: подключите кабель или Wi-Fi" end
  local port = math.random(30000, 39999)
  net.open(port)
  local id = math.random(1, 1000000000)
  if not net.send(self.server, hosting.PORT, "HOST", id, port, cmd, self.user, self.ph, ...) then
    net.close(port)
    return nil, "Нет сети"
  end
  local r = wait(port, id, self.timeout, self.quiet)
  net.close(port)
  if not r then return nil, "«" .. tostring(self.server) .. "» не отвечает: там нет хостинга (hostd) или нет сети" end
  if not r.ok then return nil, tostring(r.value) end
  return r.value == nil and true or r.value
end

function Session:info() return self:call("info") end
function Session:remove(site) return self:call("remove", site) end

--- Все файлы папки (пути относительно неё), кроме служебных .deploy.
local function files(dir, prefix, out)
  out = out or {}
  prefix = prefix or ""
  for _, name in ipairs(fs.list(dir)) do
    local p = fs.combine(dir, name)
    if fs.isDir(p) then files(p, prefix .. name .. "/", out)
    elseif name ~= ".deploy" then out[#out + 1] = prefix .. name end
  end
  return out
end

local function read(path)
  local f = fs.open(path, "rb") or fs.open(path, "r")
  if not f then return nil end
  local d = f.readAll() or ""
  f.close()
  return d
end

--- Загрузить папку dir как сайт site. onProgress(сделано, всего, файл) — по файлам.
function Session:upload(dir, site, onProgress)
  if not fs.isDir(dir) then return nil, "Нет папки " .. dir end
  -- Своё имя .mine с этого компьютера переходит хостингу
  local okL, owner = net.dns("lookup", site)
  if okL and owner == net.address() then net.dns("unregister", site) end
  local manifest, err = self:call("begin", site)
  if not manifest then return nil, err end
  local have = {}
  if type(manifest) == "string" then
    for line in manifest:gmatch("[^\n]+") do
      local p, c = line:match("^(.-)\t(%x+)$")
      if p then have[p] = c end
    end
  end
  local list = files(dir)
  local sent, total = 0, 0
  for i, rel in ipairs(list) do
    local body = read(fs.combine(dir, rel)) or ""
    if have[rel] ~= data.toHex(data.crc32(body)) then
      local parts = math.max(1, math.ceil(#body / hosting.CHUNK))
      for part = 1, parts do
        local ok, e = self:call("put", site, rel, part, parts, body:sub((part - 1) * hosting.CHUNK + 1, part * hosting.CHUNK))
        if not ok then return nil, rel .. ": " .. tostring(e) end
      end
      sent = sent + 1
      total = total + #body
    end
    if onProgress then onProgress(i, #list, rel) end
  end
  local res, e2 = self:call("finish", site, table.concat(list, "\n"))
  if not res then return nil, e2 end
  return type(res) == "table" and res.url or tostring(res), sent, total
end

function hosting.connect(server, user, password, opts)
  opts = opts or {}
  return setmetatable({ server = server, user = tostring(user or ""), ph = hex(tostring(password or "")),
    quiet = opts.quiet, timeout = opts.timeout or 5 }, Session)
end

--- Серверы с хостингом среди соседей по сети (ждёт ответы секунду).
function hosting.discover(quiet)
  local out = {}
  if net.speed() == 0 then return out end
  local port = math.random(30000, 39999)
  net.open(port)
  local id = math.random(1, 1000000000)
  local names = {}
  for _, p in ipairs(net.peers()) do
    names[p.address] = p.name
    net.send(p.address, hosting.PORT, "HOST", id, port, "hello", "", "")
  end
  local timer = os.startTimer(1)
  local skipped = {}
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "HOSTOK" and ev[6] == id then
      local v = type(ev[8]) == "table" and ev[8] or {}
      out[#out + 1] = { address = ev[2], name = v.name or names[ev[2]] or ev[2]:sub(1, 8), sites = v.sites or 0, free = v.free }
    elseif (ev[1] == "timer" and ev[2] == timer) or ev[1] == "terminate" then
      break
    elseif not quiet then
      skipped[#skipped + 1] = ev
    end
  end
  net.close(port)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  return out
end

return hosting

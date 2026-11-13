-- Майннет: запросы к сайтам (порт 80). Адрес — имя.mine, имя компьютера или его адрес, дальше путь:
--   shop.mine/index.page,  shop.mine/order.lua
-- http.get(адрес, [параметры], [onProgress]) → текст, код (200/404/500), тип ("page" | "text" | "file")
--                                            или nil, ошибка
local http = {}
http.PORT = 80
http.CHUNK = 6000

--- Разбирает адрес на компьютер и путь: "shop.mine/x" → "shop.mine", "/x".
function http.split(url)
  url = tostring(url or ""):gsub("^mine://", "")
  local host, path = url:match("^([^/]+)(/.*)$")
  if not host then host, path = url, "/" end
  return host, path
end

--- Собирает адрес ссылки относительно страницы base.
function http.resolve(base, link)
  link = tostring(link or "")
  if link:match("^mine://") then return (link:gsub("^mine://", "")) end
  local bhost, bpath = http.split(base)
  if link:sub(1, 1) == "/" then return bhost .. link end
  if link:find("^[%w%-%.]+%.mine") or (not link:find("/") and not link:find("%.%a+$")) and link:find("%.") then
    return link
  end
  local dir = bpath:match("^(.*/)") or "/"
  return bhost .. dir .. link
end

function http.get(url, params, onProgress, timeout)
  if net.speed() == 0 then return nil, "Нет сети: подключите кабель или Wi-Fi" end
  local host, path = http.split(url)
  if host == "" then return nil, "Пустой адрес" end
  local port = math.random(40000, 49999)
  net.open(port)
  local id = math.random(1, 1000000000)
  if not net.send(host, http.PORT, "GET", id, port, path, params or {}) then
    net.close(port)
    return nil, "Нет сети"
  end
  local parts, got, total, status, ctype = {}, 0, 1, nil, nil
  local wait = timeout or 4
  local timer = os.startTimer(wait)
  local skipped, err = {}, nil
  while true do
    local ev = table.pack(os.pullEventRaw())
    if ev[1] == "net_message" and ev[3] == port and ev[5] == "RESP" and ev[6] == id then
      local part = ev[7]
      total, status, ctype = ev[8], ev[9], ev[10]
      if not parts[part] then
        parts[part] = ev[11] or ""
        got = got + 1
      end
      if onProgress then onProgress(got, total) end
      if got >= total then break end
      os.cancelTimer(timer)
      timer = os.startTimer(wait)
    elseif ev[1] == "timer" and ev[2] == timer then
      err = got > 0 and "Ответ оборвался: связь слишком медленная или пропала"
        or ("«" .. host .. "» не отвечает: нет такого сайта, сайт выключен или до него нет сети")
      break
    elseif ev[1] == "terminate" then
      err = "Прервано"
      break
    else
      skipped[#skipped + 1] = ev
    end
  end
  os.cancelTimer(timer)
  net.close(port)
  for _, e in ipairs(skipped) do os.queueEvent(table.unpack(e, 1, e.n)) end
  if err then return nil, err end
  local body = {}
  for i = 1, total do body[i] = parts[i] or "" end
  return table.concat(body), status, ctype
end

--- Ответ сервера: делит на части по 6 КБ.
function http.reply(to, port, id, status, ctype, body)
  body = body or ""
  local n = math.max(1, math.ceil(#body / http.CHUNK))
  for i = 1, n do
    net.send(to, port, "RESP", id, i, n, status, ctype, body:sub((i - 1) * http.CHUNK + 1, i * http.CHUNK))
  end
end

return http

-- Принять файлы по сети: recv [порт]. Отправка — программа send. Ctrl+T — выход
local port = tonumber((...) or "") or 21
if net.speed() == 0 then print("Нет сети: подключите кабель или Wi-Fi") return end
net.open(port)
print("Жду файлы на порту " .. port .. " (Ctrl+T — выход)")

local files = {}
local function freeName(name)
  name = name:gsub("[/\\:]", "_")
  if name == "" or name == "." or name == ".." then name = "file" end
  local path = shell.resolve(name)
  local base, ext = name:match("^(.-)(%.[^.]*)$")
  base, ext = base or name, ext or ""
  local n = 1
  while fs.exists(path) do
    path = shell.resolve(base .. "_" .. n .. ext)
    n = n + 1
  end
  return path
end

local ok, err = pcall(function()
  while true do
    local ev = { os.pullEvent("net_message") }
    local from, p, kind, id = ev[2], ev[3], ev[5], ev[6]
    if p == port and id then
      local key = from .. ":" .. tostring(id)
      if kind == "file_begin" then
        files[key] = { name = tostring(ev[7]), size = tonumber(ev[8]) or 0, parts = {}, got = 0 }
        print("От " .. from:sub(1, 8) .. ": " .. files[key].name .. " (" .. files[key].size .. " байт)")
        net.send(from, port, "file_ok", id)
      elseif kind == "file_part" and files[key] then
        local f = files[key]
        if f.parts[ev[7]] == nil then f.got = f.got + 1 end
        f.parts[ev[7]] = ev[8]
        if f.got % 8 == 0 then net.send(from, port, "file_ack", id, f.got) end   -- отправитель шлёт дальше
      elseif kind == "file_end" and files[key] then
        local f = files[key]
        files[key] = nil
        local count, missing = tonumber(ev[7]) or 0, false
        for i = 1, count do if type(f.parts[i]) ~= "string" then missing = true end end
        local content = missing and "" or table.concat(f.parts, "", 1, count)
        if missing or data.crc32(content) ~= ev[8] or #content ~= f.size then
          printError("  " .. f.name .. ": файл повреждён, не сохранён")
          net.send(from, port, "file_done", id, false, missing and "дошли не все части" or "контрольная сумма не совпала")
        else
          local path = freeName(f.name)
          local h = fs.open(path, "wb")
          h.write(content)
          h.close()
          if term.isColor() then term.setTextColor(colors.lime) end
          print("  сохранён: " .. path)
          term.setTextColor(colors.white)
          net.send(from, port, "file_done", id, true)
        end
      end
    end
  end
end)
net.close(port)
if not ok and err ~= "Прервано" then printError(err) end

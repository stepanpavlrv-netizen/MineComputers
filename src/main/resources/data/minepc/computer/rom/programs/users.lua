-- Пользователи компьютера.
--   users                       — кто есть и кто вы
--   users add <имя> [admin]     — добавить (спросит пароль). Первый пользователь — администратор,
--                                 и с этого момента при включении компьютер спрашивает вход
--   users del <имя>             — удалить
--   users admin <имя> on|off    — сделать администратором / обычным
-- Менять пользователей может администратор после sudo (пока пользователей нет — кто угодно).
local cmd, name, extra = ...

local function color(c) if term.isColor() then term.setTextColor(c) end end
local function askPassword()
  write("Пароль для " .. name .. ": ")
  local a = read("*")
  write("Ещё раз: ")
  local b = read("*")
  if a ~= b then printError("Пароли не совпали") return nil end
  return a
end

if not cmd then
  local list = auth.users()
  if #list == 0 then
    print("Пользователей нет — компьютер открыт для всех.")
    print("Защитить паролем: users add <имя>")
    return
  end
  color(colors.yellow)
  print("Пользователи:")
  color(colors.white)
  for _, u in ipairs(list) do
    print("  " .. u.name .. (u.admin and "  (администратор)" or ""))
  end
  local me = auth.user()
  print(me and ("Вы: " .. me .. (auth.elevated() and ", права администратора (sudo)" or "")) or "Вы не вошли")
  return
end

if cmd == "add" then
  if not name then print("users add <имя> [admin]") return end
  local first = auth.open()
  local pw = askPassword()
  if not pw then return end
  local ok, err = auth.add(name, pw, extra == "admin")
  if not ok then printError(err) return end
  color(colors.lime)
  print("Пользователь " .. name .. " добавлен" .. ((first or extra == "admin") and " (администратор)" or ""))
  color(colors.white)
  if first then
    print("Теперь при включении компьютер спрашивает вход. Вы уже вошли как " .. name .. ".")
    print("Вход по сети: sshd, с другого компьютера — connect.")
  end
elseif cmd == "del" then
  if not name then print("users del <имя>") return end
  local ok, err = auth.remove(name)
  if not ok then printError(err) return end
  print("Пользователь " .. name .. " удалён")
elseif cmd == "admin" then
  if not name or (extra ~= "on" and extra ~= "off") then print("users admin <имя> on|off") return end
  local ok, err = auth.setAdmin(name, extra == "on")
  if not ok then printError(err) return end
  print(name .. (extra == "on" and " — администратор" or " — обычный пользователь"))
else
  printError("Не знаю " .. cmd .. ". Смотрите: users, users add, users del, users admin")
end

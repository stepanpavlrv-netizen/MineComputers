-- Журнал входов: кто входил, неверные пароли, закрытые адреса, sudo, изменения пользователей.
--   journal [N]                     — последние N записей (по умолчанию — сколько влезает на экран)
--   journal notify <имя@компьютер>  — слать письмо при атаке (5 ошибок подряд), journal notify off — не слать
-- Смотрит администратор: sudo journal.
local a, b = ...
if a == "notify" then
  if not b then
    local cur = auth.notify()
    print(cur ~= "" and ("Письма при атаке: " .. cur) or "Письма при атаке не шлются. journal notify <имя@компьютер>")
    return
  end
  local ok, err = auth.notify(b == "off" and "" or b)
  if not ok then printError(err) return end
  print(b == "off" and "Письма при атаке выключены" or ("Письмо при атаке — на " .. b .. " (нужен запущенный sshd)"))
  return
end
local _, h = term.getSize()
local n = tonumber(a) or (h - 2)
local lines, err = auth.journal(n)
if not lines then printError(err) return end
if #lines == 0 then print("Журнал пуст") return end
for _, line in ipairs(lines) do
  if term.isColor() then
    if line:find("неверный") or line:find("закрыт") or line:find("нет пользователя") or line:find("не администратор") then
      term.setTextColor(colors.red)
    elseif line:find("— ok") then
      term.setTextColor(colors.lime)
    elseif line:find("файрвол") then
      term.setTextColor(colors.orange)
    else
      term.setTextColor(colors.white)
    end
  end
  print(line)
end
if term.isColor() then term.setTextColor(colors.white) end

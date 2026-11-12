-- Управляемые коммутаторы в сети:
--   switch                       — какие есть
--   switch <id>                  — порты: связь, кто воткнут, VLAN
--   switch <id> <порт> on|off    — включить/выключить порт
--   switch <id> <порт> <цвет>    — VLAN (red, blue, lime…; none — общий)
local id, port, what = ...
local isColor = term.isColor()
local function c(col) if isColor then term.setTextColor(col) end end
local kinds = { desk = "коммутатор", rack = "стоечный коммутатор", rackbus = "шина стоек", router = "Wi-Fi роутер", radio = "радиомодем" }
local sides = { front = "спереди", back = "сзади", left = "слева", right = "справа", cord = "корд", bus = "шина" }

if not id then
  local list = switch.list()
  if #list == 0 then print("Коммутаторов в сети не видно") return end
  for _, s in ipairs(list) do
    c(colors.cyan) write(s.id) c(colors.white)
    print(string.format("  %s, занято портов: %d", kinds[s.kind] or s.kind, s.ports))
  end
  c(colors.lightGray) print("switch <id> — порты") c(colors.white)
  return
end

local ports, err = switch.ports(id)
if not ports then printError(err) return end

if not port then
  if #ports == 0 then print("Ничего не воткнуто") end
  for _, p in ipairs(ports) do
    c(not p.enabled and colors.red or p.link and colors.lime or colors.gray)
    write(string.format("%2d ", p.port))
    c(colors.white)
    write((sides[p.side] or p.side) .. " " .. p.n)
    if p.vlan ~= "" then c(colors.yellow) write(" [" .. p.vlan .. "]") end
    c(colors.lightGray)
    print("  " .. (p.enabled and "" or "выкл  ") .. (p.device ~= "" and p.device or "—"))
  end
  c(colors.white)
  return
end

port = tonumber(port)
if not port then printError("Порт — число из списка: switch " .. id) return end
local enabled = true
for _, p in ipairs(ports) do if p.port == port then enabled = p.enabled end end
local vlan
if what == "on" then enabled = true
elseif what == "off" then enabled = false
elseif what then vlan = what
else print("Что сделать: on, off или цвет VLAN") return end
local ok, e = switch.set(id, port, enabled, vlan)
if ok then c(colors.lime) print("Готово") c(colors.white) else printError(e) end

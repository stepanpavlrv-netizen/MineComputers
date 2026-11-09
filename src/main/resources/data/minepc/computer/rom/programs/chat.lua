-- Чат по сети: chat [канал]. Enter — отправить, Ctrl+T — выйти.
local channel = tonumber(...) or 1
local PORT = 4000 + channel
if net.speed() == 0 then print("Кабель не подключён — чату некуда писать") return end
net.open(PORT)
local me = net.hostname() ~= "" and net.hostname() or net.address():sub(1, 8)
local w, h = term.getSize()
local lines, input = {}, ""
local palette = { colors.cyan, colors.orange, colors.lime, colors.pink, colors.yellow, colors.lightBlue }

local function colorFor(name)
  local s = 0
  for i = 1, #name do s = s + name:byte(i) end
  return palette[s % #palette + 1]
end

local function add(from, text)
  lines[#lines + 1] = { from = tostring(from), text = tostring(text) }
end

local function redraw()
  term.setBackgroundColor(colors.black)
  term.clear()
  term.setCursorPos(1, 1)
  term.setBackgroundColor(colors.blue) term.setTextColor(colors.white) term.write(" Чат ")
  term.setBackgroundColor(colors.gray)
  local head = string.format(" канал %d · %s · в сети %d", channel, me, #net.peers())
  term.write(head .. string.rep(" ", math.max(0, w - 5 - unicode.len(head))))
  term.setBackgroundColor(colors.black)
  local rows = h - 3
  local first = math.max(1, #lines - rows + 1)
  for i = first, #lines do
    local l = lines[i]
    term.setCursorPos(1, 2 + i - first + 1)
    term.setTextColor(colorFor(l.from)) term.write("[" .. l.from .. "] ")
    term.setTextColor(colors.white) term.write(unicode.sub(l.text, 1, math.max(1, w - unicode.len(l.from) - 3)))
  end
  term.setCursorPos(1, h)
  term.setTextColor(colors.yellow) term.write("> ")
  term.setTextColor(colors.white) term.write(unicode.sub(input, -(w - 3)))
  term.setCursorBlink(true)
end

add("чат", "канал " .. channel .. ": пиши и жми Enter")
redraw()
while true do
  local ev = { os.pullEventRaw() }
  local e = ev[1]
  if e == "terminate" then break
  elseif e == "char" then input = input .. ev[2]
  elseif e == "paste" then input = input .. ev[2]
  elseif e == "key" and ev[2] == "backspace" then input = unicode.sub(input, 1, -2)
  elseif e == "key" and ev[2] == "enter" and input ~= "" then
    if net.broadcast(PORT, me, input) then add(me, input) else add("чат", "не отправлено: нет кабеля") end
    input = ""
  elseif e == "net_message" and ev[3] == PORT then
    add(ev[5] or ev[2]:sub(1, 8), ev[6] or "")
  end
  redraw()
end
net.close(PORT)
term.setBackgroundColor(colors.black)
term.setTextColor(colors.white)
term.clear()
term.setCursorPos(1, 1)

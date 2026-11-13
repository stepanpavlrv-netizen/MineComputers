-- Скачать файл с сайта Майннета: get <адрес/путь> [куда]
--   get shop.mine/map.png        → ./map.png
--   get shop.mine/ /tmp/home.page
local url, dest = ...
if not url then print("Использование: get <адрес/путь> [файл]") return end
local http = require("http")
local _, path = http.split(url)
dest = dest or (path:match("([^/]+)$") or "index.page")
if not dest:match("^/") then dest = (shell and shell.resolve) and shell.resolve(dest) or ("/" .. dest) end
local x, y = term.getCursorPos()
local t0 = os.clock()
local body, code = http.get(url, nil, function(got, total)
  term.setCursorPos(1, y)
  term.clearLine()
  term.write(string.format("Скачиваю: %d%%", math.floor(got * 100 / total)))
end, 6)
term.setCursorPos(1, y)
term.clearLine()
if not body then printError(code) return end
if code ~= 200 then printError("Сайт ответил " .. tostring(code)) return end
local f, err = fs.open(dest, "wb")
if not f then printError(err) return end
f.write(body)
f.close()
print(string.format("Сохранено: %s (%d байт за %.1f с)", dest, #body, os.clock() - t0))

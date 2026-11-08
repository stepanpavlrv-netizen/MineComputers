-- OpenOS: библиотека colors (номера те же, что и в MineComputers)
local c = {}
for k, v in pairs(colors) do c[k] = v end
c.silver = c.lightGray
c.lightblue = c.lightBlue
for k, v in pairs(colors) do c[v] = k end
return c

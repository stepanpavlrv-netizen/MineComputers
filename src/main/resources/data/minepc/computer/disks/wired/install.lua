-- Установка Wired / SWired с дискеты или флешки: сам установщик — system/install.lua (он же попадает в /wired,
-- чтобы установленная система могла записать установочную флешку).
local src = ...
if not src then
  for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
    if fs.exists(d .. "/system/boot.lua") then src = d break end
  end
end
if not src then printError("Не нашёл файлы Wired на дисках") return end
if not fs.exists(src .. "/system/install.lua") then printError("Нет установщика: " .. src .. "/system/install.lua") return end
os.run({}, src .. "/system/install.lua", src)

-- Установочная дискета Wired: при загрузке с неё запускается установка.
for _, d in ipairs({ "/disk", "/disk2", "/disk3", "/usb", "/usb2" }) do
  if fs.exists(d .. "/install.lua") and fs.exists(d .. "/system/boot.lua") then
    os.run({}, d .. "/install.lua", d)
    return
  end
end

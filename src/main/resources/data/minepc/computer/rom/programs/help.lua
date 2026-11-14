local topics = {
  { "ls [папка]", "список файлов" },
  { "cd <папка>", "перейти в папку (cd .. — на уровень выше)" },
  { "cat <файл>", "показать содержимое файла" },
  { "edit <файл>", "редактор текста и программ" },
  { "lua", "интерактивный Lua: считать и пробовать код" },
  { "mkdir, rm, cp, mv", "создать, удалить, копировать, переместить" },
  { "drives", "накопители и свободное место" },
  { "info", "железо компьютера и скорость" },
  { "label [имя]", "имя компьютера" },
  { "сеть", "ip, peers, ping, chat, wifi, send/recv, switch, bmc, ioc" },
  { "майннет", "browse, httpd, dns, mail, get, cam" },
  { "sh", "новая командная строка (exit — назад)" },
  { "ps, top, kill N", "процессы, нагрузка, остановить" },
  { "reboot / shutdown", "перезагрузить / выключить" },
}
local w = term.getSize()
for _, t in ipairs(topics) do
  if term.isColor() then term.setTextColor(colors.yellow) end
  write(t[1])
  term.setTextColor(colors.white)
  print(" — " .. t[2])
end
print("Запуск программы: hello.lua -> hello")
print("Ctrl+T — прервать, Ctrl+V — вставить")

for _, m in ipairs(fs.mounts()) do
  if term.isColor() then term.setTextColor(colors.yellow) end
  write(m.path)
  term.setTextColor(colors.white)
  if m.readOnly then
    print("  " .. m.label .. ", только чтение")
  else
    local speed = m.speed and m.speed > 0 and (", " .. textutils.formatSize(m.speed) .. "/с") or ""
    print("  " .. m.label .. ": занято " .. textutils.formatSize(m.used) .. " из " .. textutils.formatSize(m.capacity) .. speed)
  end
end

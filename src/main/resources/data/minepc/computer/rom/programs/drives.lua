for _, m in ipairs(fs.mounts()) do
  if term.isColor() then term.setTextColor(colors.yellow) end
  write(m.path)
  term.setTextColor(colors.white)
  if m.readOnly then
    print("  " .. m.label .. ", только чтение")
  else
    print("  " .. m.label .. ": занято " .. textutils.formatSize(m.used) .. " из " .. textutils.formatSize(m.capacity))
  end
end

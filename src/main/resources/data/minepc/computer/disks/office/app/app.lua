return {
  id = "office",
  name = "Офис",
  version = "1.0",
  main = "docs.lua",
  icon = "doc",
  description = "Документы и Таблицы",
  wired = "1.0",
  programs = {
    { id = "docs", name = "Документы", main = "docs.lua", icon = "doc", opens = { "wdoc" } },
    { id = "tables", name = "Таблицы", main = "tables.lua", icon = "table", opens = { "wtab" } },
  },
}

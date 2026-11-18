-- Wired: значки программ. Значок — 9×12 полупикселей (на экране 9×6 клеток, почти квадрат).
-- Буквы — цвета из PAL, точка — прозрачно. Свой значок программы — файл icon.pix (12 строк таких букв).
local I = {}

I.PAL = {
  y = 0xffc857, Y = 0xc9932a, A = 0xffe3a3, r = 0xff4f5e, R = 0xb8323f, c = 0x4fd1e8, C = 0x2a8ea3,
  w = 0xece8f4, W = 0xb9b3cc, g = 0x8d87a3, G = 0x5d5775, d = 0x3a3550, k = 0x14111d, s = 0xc9c3d9,
  b = 0x6b95ff, B = 0x3a5fc0, n = 0x5fd38a, N = 0x2f9a5a, o = 0xff8a3d, p = 0xc77dff, P = 0x8a4fc0,
}

I.ART = {
  files = { ".........", ".........", ".yyyy....", "yYYYYy...", "yyyyyyyyy", "yAAAAAAAy", "yyyyyyyyy", "yyyyyyyyy",
            "yyyyyyyyy", "yyyyyyyyy", "YYYYYYYYY", "........." },
  term = { ".........", "ddddddddd", "dkkkkkkkd", "dkckkkkkd", "dkkckkkkd", "dkckkkkkd", "dkkkkkkkd", "dkkkccckd",
           "dkkkkkkkd", "ddddddddd", "...GGG...", ".GGGGGGG." },
  cam = { ".........", ".........", "Gssssssr.", "Gwwwwwwwc", "Gwwwwwwwc", "Gssssssr.", ".GGG.....", "..dd.....",
          "..dd.....", ".dddd....", ".........", "........." },
  web = { "...ccc...", ".ccCcCcc.", ".cCcCcCc.", "cCccCccCc", "cCCCCCCCc", "cCccCccCc", "cCccCccCc", "cCCCCCCCc",
          "cCccCccCc", ".cCcCcCc.", ".ccCcCcc.", "...ccc..." },
  mail = { ".........", ".........", "wwwwwwwww", "wgwwwwwgw", "wwgwwwgww", "wwwgwgwww", "wwwwrwwww", "wwwwwwwww",
           "wwwwwwwww", "WWWWWWWWW", ".........", "........." },
  site = { ".........", "ddddddddd", "drrrrrrrd", "dwwwwwwwd", "dccccwggd", "dccccwwwd", "dccccwggd", "dwwwwwwwd",
           "dgggggggd", "dwwwwwwwd", "ddddddddd", "........." },
  doc = { ".wwwwww..", ".wwwwwwW.", ".wbbbbwww", ".wwwwwwww", ".wggggggw", ".wwwwwwww", ".wggggggw", ".wwwwwwww",
          ".wgggggww", ".wwwwwwww", ".WWWWWWWW", "........." },
  table = { ".........", "nnnnnnnnn", "nnnnnnnnn", "wwgwwgwww", "ggggggggg", "wwgwwgwww", "ggggggggg", "wwgwwgwww",
            "ggggggggg", "wwgwwgwww", "WWWWWWWWW", "........." },
  gear = { "....g....", "..g.g.g..", "...ggg...", ".ggggggg.", ".gggdggg.", "gggdddggg", "gggdddggg", ".gggdggg.",
           ".ggggggg.", "...ggg...", "..g.g.g..", "....g...." },
  trash = { ".........", "...ggg...", ".ggggggg.", ".........", ".gdgdgdg.", ".gdgdgdg.", ".gdgdgdg.", ".gdgdgdg.",
            ".gdgdgdg.", ".ggggggg.", ".........", "........." },
  disk = { ".........", "rrssssrr.", "rrssdsrrr", "rrssssrrr", "rrrrrrrrr", "rrrrrrrrr", "rwwwwwwrr", "rwggggwrr",
           "rwwwwwwrr", "rwggggwrr", "rrrrrrrrr", "........." },
  task = { ".........", "ddddddddd", "dkkkkkkkd", "dkkkkknkd", "dkkkkknkd", "dkknkknkd", "dkknknnkd", "dknnnnnkd",
           "dkkkkkkkd", "ddddddddd", ".........", "........." },
  chat = { ".........", ".ccccccc.", "ccccccccc", "ckckkckcc", "ccccccccc", ".ccccccc.", "..cc.....", ".c.......",
           ".........", "....rrrrr", "...rrrrrr", "....r...." },
  net = { "....n....", "...nnn...", "....g....", "....g....", "gggggggg.", "g...g...g", "g...g...g", "n...n...n",
          "nn.nnn.nn", ".........", ".........", "........." },
  pult = { ".........", "ddddddddd", "dkkkkkkkd", "dkrkkkykd", "dkkkkkkkd", "dgggggggd", "dgwgdgwgd", "dgggggggd",
           "dgdgwgdgd", "dgggggggd", "ddddddddd", "........." },
  paint = { ".......y.", "......yY.", ".....yY..", "....yY...", "...yY....", "..dd.....", ".rdd.....", "rrr......",
            "rrb......", ".bbcc....", "..ccnn...", "...nn...." },
  music = { ".........", "....ppppp", "....pPPPp", "....p...p", "....p...p", "....p...p", "..ppp.ppp", ".pppp.ppp",
            ".ppp..pp.", ".........", ".........", "........." },
  games = { ".........", ".........", ".ddddddd.", "dgggggggd", "dgdgggrgd", "ddddgrgrd", "dgdgggrgd", "dgggggggd",
            ".ddd.ddd.", ".........", ".........", "........." },
  archive = { ".........", "YYYYYYYYY", "yyyyyyyyy", "YYYYYYYYY", "yyyykyyyy", "yyyykyyyy", "yyyyyyyyy", "yyyyyyyyy",
              "yyyyyyyyy", "YYYYYYYYY", ".........", "........." },
  calc = { ".ddddddd.", ".dnnnnnd.", ".dnnnnnd.", ".ddddddd.", ".dgdgdgd.", ".ddddddd.", ".dgdgdgd.", ".ddddddd.",
           ".dgdgdrd.", ".ddddddd.", ".dgdgdrd.", ".ddddddd." },
  clock = { "...www...", ".wwwwwww.", ".wwwkwww.", "wwwwkwwww", "wwwwkwwww", "wwwwkkkww", "wwwwwwwww", "wwwwwwwww",
            ".wwwwwww.", ".wwwwwww.", "...www...", "........." },
  host = { ".ddddddd.", ".dkkkkkd.", ".dknkkgd.", ".dkkkkkd.", ".ddddddd.", ".dkkkkkd.", ".dknkkgd.", ".dkkkkkd.",
           ".ddddddd.", ".dkckkgd.", ".ddddddd.", "..d...d.." },
  srv = { ".ddddddd.", ".dkkkkkd.", ".dkcckkd.", ".dkkkkkd.", ".ddddddd.", ".dkkkkkd.", ".dkcknkd.", ".dkkkkkd.",
          ".ddddddd.", ".dkcckkd.", ".ddddddd.", "..d...d.." },
  svc = { ".........", ".nnn.....", "nNNNn.ggg", "nNkNn.ggg", "nNNNn....", ".nnn.....", ".........", ".rrr.....",
          "rRRRr.ggg", "rRkRr.ggg", "rRRRr....", ".rrr....." },
  health = { ".........", "...bb....", "..bbbb...", ".bbBBbb..", ".bbBBbb..", "bbbbbbbbb", "bbbwbwbbb", "bbwbwbwbb",
             ".bbbbbb..", "..bbbb...", "...bb....", "........." },
  journal = { ".yyyyyyy.", ".yAAAAAy.", ".yAgggAy.", ".yAAAAAy.", ".yAgggAy.", ".yAAAAAy.", ".yArrrAy.", ".yAAAAAy.",
              ".yAgggAy.", ".yAAAAAy.", ".yyyyyyy.", "........." },
  users = { ".........", "..ppp....", ".ppppp...", ".ppppp.cc", "..ppp.ccc", "......ccc", ".ppppp.cc", "ppppppp..",
            "ppppppp..", "ppppppp..", ".........", "........." },
  backup = { ".........", "..oooo...", ".o....o..", "o......o.", "o..o...o.", "o.ooo..o.", "...o.....", "...o..ooo",
             "ddddd.o.o", "dkkkd.ooo", "ddddd....", "........." },
  servers = { ".ddddddd.", ".dkcknkd.", ".ddddddd.", ".dkcknkd.", ".ddddddd.", ".dkcknkd.", ".ddddddd.", "....c....",
              "...ccc...", "..c.c.c..", ".c..c..c.", "........." },
  app = { ".........", "ddddddddd", "dpppppppd", "dwwwwwwwd", "dwppwppwd", "dwppwppwd", "dwwwwwwwd", "dwppwppwd",
          "dwppwppwd", "dwwwwwwwd", "ddddddddd", "........." },
}

-- Значок одной клеткой: символ и цвет
I.MINI = {
  files = { "■", 0xffc857 }, term = { ">", 0x4fd1e8 }, cam = { "◉", 0xff4f5e }, web = { "◍", 0x4fd1e8 },
  mail = { "✉", 0xece8f4 }, site = { "▦", 0xff4f5e }, doc = { "≡", 0x6b95ff }, table = { "▦", 0x5fd38a },
  gear = { "✲", 0x8d87a3 }, task = { "▥", 0x5fd38a }, trash = { "▾", 0x8d87a3 }, disk = { "▣", 0xff4f5e },
  app = { "◆", 0xc77dff }, chat = { "◌", 0x4fd1e8 }, net = { "⇄", 0x5fd38a }, pult = { "⚡", 0xffc857 },
  paint = { "✎", 0xffc857 }, music = { "♪", 0xc77dff }, games = { "♠", 0xff4f5e }, archive = { "▤", 0xffc857 },
  calc = { "±", 0x5fd38a }, clock = { "◷", 0xece8f4 }, host = { "☁", 0x4fd1e8 },
  srv = { "▤", 0x4fd1e8 }, svc = { "●", 0x5fd38a }, health = { "♥", 0x6b95ff }, journal = { "≡", 0xffc857 },
  users = { "☺", 0xc77dff }, backup = { "↺", 0xff8a3d }, servers = { "▤", 0x4fd1e8 },
}

--- Значок программы из файла icon.pix (или nil).
function I.load(path)
  if not fs.exists(path) then return nil end
  local f = fs.open(path, "r")
  if not f then return nil end
  local art = {}
  for line in (f.readAll() or ""):gmatch("[^\r\n]+") do
    if #art < 12 then art[#art + 1] = line:sub(1, 9) end
  end
  f.close()
  if #art == 0 then return nil end
  return art
end

return I

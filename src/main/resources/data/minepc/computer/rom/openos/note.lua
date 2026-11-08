-- OpenOS: библиотека note (звука у компьютера пока нет — ноты просто пропускаются)
local note = {}
function note.play() end
function note.freq(n) return 440 end
function note.midi(n) return 69 end
return note

#!/bin/bash
# Скриншоты из настоящей игры: мир с компьютером, экран терминала, окно системного блока.
# Мир берётся у проверочного сервера (build/smoke-server/world), игра заходит в него сама.
# Плюс видео: когда игра пишет run/record.txt (координаты окна), ffmpeg записывает 11 секунд экрана.
set -e
mkdir -p run/saves
rm -rf run/saves/ci run/screenshots run/record.txt
cp -r build/smoke-server/world run/saves/ci
cat > run/options.txt <<'OPT'
lang:ru_ru
onboardAccessibility:false
tutorialStep:none
renderDistance:4
simulationDistance:5
guiScale:2
pauseOnLostFocus:false
menuBackgroundBlurriness:0
graphicsMode:0
renderClouds:"false"
maxFps:120
enableVsync:false
OPT
command -v ffmpeg >/dev/null || (sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg) || true
# Звук игры: звуковой карты в CI нет, поэтому OpenAL пишет всё, что слышит игрок, в файл
rm -f run/game_audio.wav run/record_start.txt
cat > "$PWD/run/alsoft.conf" <<CONF
[general]
drivers = wave
[wave]
file = $PWD/run/game_audio.wav
CONF
cp "$PWD/run/alsoft.conf" "$HOME/.alsoftrc"
export ALSOFT_CONF="$PWD/run/alsoft.conf"
timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" bash -c '
  (
    for i in $(seq 1 3000); do [ -f run/record.txt ] && break; sleep 0.2; done
    if [ -f run/record.txt ]; then
      read X Y W H < run/record.txt
      mkdir -p run/screenshots
      echo "Запись видео: окно ${W}x${H} в ($X,$Y), экран $DISPLAY"
      date +%s.%N > run/record_start.txt
      ffmpeg -y -loglevel info -nostats -f x11grab -draw_mouse 0 -framerate 30 -video_size ${W}x${H} -i ${DISPLAY}+${X},${Y} \
        -t 11 -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p run/screenshots/minepc_bunker.mp4 2> run/ffmpeg.log \
        && echo "Видео записано"
      # Точное время первого кадра — его пишет сам ffmpeg (запускается он ещё ~0.15 с после date)
      T=$(grep -o "start: [0-9.]*" run/ffmpeg.log | head -1 | cut -d" " -f2)
      [ -n "$T" ] && echo "$T" > run/record_start.txt
    fi
  ) &
  ./gradlew runClient -PciShots > shots.log 2>&1
  wait
' || true
# Склеить видео со звуком игры: звук пишется с запуска игры, видео — с момента record_start
if [ -f run/game_audio.wav ] && [ -f run/record_start.txt ] && [ -f run/screenshots/minepc_bunker.mp4 ]; then
  END=$(python3 -c "import os;print(os.path.getmtime('run/game_audio.wav'))")
  DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 run/game_audio.wav)
  START=$(cat run/record_start.txt)
  OFF=$(python3 -c "print(max(0.0, $START - ($END - $DUR)))")
  echo "Звук игры: ${DUR} с, видео начинается с ${OFF} с"
  ffmpeg -y -loglevel error -i run/screenshots/minepc_bunker.mp4 -ss "$OFF" -i run/game_audio.wav \
    -map 0:v -map 1:a -c:v copy -c:a aac -b:a 192k -shortest run/screenshots/minepc_bunker_sound.mp4 && echo "Видео со звуком готово"
  # и отдельно звук того же отрезка — для проверки громкости
  ffmpeg -y -loglevel error -ss "$OFF" -t 11 -i run/game_audio.wav -ac 1 -ar 22050 run/screenshots/minepc_audio_check.wav || true
fi
ls -la run/screenshots || true
grep -E "minepc|Exception" run/logs/latest.log | grep -v DEBUG | head -30 || true

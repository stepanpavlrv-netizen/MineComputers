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
timeout 700 xvfb-run -a -s "-screen 0 1280x720x24" bash -c '
  (
    for i in $(seq 1 3000); do [ -f run/record.txt ] && break; sleep 0.2; done
    if [ -f run/record.txt ]; then
      read X Y W H < run/record.txt
      mkdir -p run/screenshots
      echo "Запись видео: окно ${W}x${H} в ($X,$Y), экран $DISPLAY"
      ffmpeg -y -loglevel error -f x11grab -draw_mouse 0 -framerate 30 -video_size ${W}x${H} -i ${DISPLAY}+${X},${Y} \
        -t 11 -c:v libx264 -preset veryfast -crf 20 -pix_fmt yuv420p run/screenshots/minepc_bunker.mp4 \
        && echo "Видео записано"
    fi
  ) &
  ./gradlew runClient -PciShots > shots.log 2>&1
  wait
' || true
ls -la run/screenshots || true
grep -E "minepc|Exception" run/logs/latest.log | grep -v DEBUG | head -30 || true

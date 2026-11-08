#!/bin/bash
# Скриншоты из настоящей игры: мир с компьютером, экран терминала, окно системного блока.
# Мир берётся у проверочного сервера (build/smoke-server/world), игра заходит в него сама.
set -e
mkdir -p run/saves
rm -rf run/saves/ci run/screenshots
cp -r build/smoke-server/world run/saves/ci
cat > run/options.txt <<'OPT'
lang:ru_ru
onboardAccessibility:false
tutorialStep:none
renderDistance:4
simulationDistance:5
guiScale:2
pauseOnLostFocus:false
OPT
timeout 600 xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient -PciShots > shots.log 2>&1 || true
ls -la run/screenshots || true
grep -E "minepc|Exception" run/logs/latest.log | grep -v DEBUG | head -30 || true

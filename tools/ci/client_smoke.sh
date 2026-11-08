#!/bin/bash
# Запуск клиента Minecraft с модом на виртуальном экране: проверяем, что игра доходит до главного меню
# и в журнале нет ошибок загрузки наших моделей, текстур и блоков.
mkdir -p run
echo "lang:ru_ru" > run/options.txt
(xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew runClient > client.log 2>&1 &)
LOG=run/logs/latest.log
for i in $(seq 1 150); do
  if [ -f $LOG ] && grep -q -E "Created: .*minecraft:textures/atlas/blocks.png" $LOG; then
    sleep 25; break
  fi
  sleep 2
done
pkill -f "net.neoforged" || true
pkill -f GradleWorkerMain || true
sleep 3
echo "--- строки журнала с ошибками ---"
grep -E "ERROR|Exception|Missing|missing|minepc" $LOG | grep -v "DEBUG\|Narrator\|flite\|OpenAL\|sound\|Sound\|sessionserver\|SocketTimeout" | head -60
if grep -E "Exception|ERROR" $LOG | grep -q -i "minepc"; then
  echo "Есть ошибки, связанные с модом"; exit 1
fi
grep -q "blocks.png-atlas" $LOG || { echo "Клиент не загрузил ресурсы"; tail -80 client.log; exit 1; }
# Звука и озвучки на виртуальной машине нет — эти ошибки не наши
echo "Клиент запустился без ошибок мода"

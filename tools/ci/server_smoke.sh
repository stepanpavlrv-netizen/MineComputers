#!/bin/bash
# Проверка готового .jar мода на настоящем сервере NeoForge (как у игроков, а не в среде разработки).
# Ставит сервер, кладёт мод в mods/, запускает, ждёт загрузки мира и проверяет журнал.
set -e
NEO=$(grep '^neo_version' gradle.properties | cut -d= -f2)
JAR=$(ls build/libs/*.jar | grep -v sources | head -1)
DIR=build/smoke-server
rm -rf $DIR && mkdir -p $DIR/mods
curl -sSfL -o $DIR/installer.jar "https://maven.neoforged.net/releases/net/neoforged/neoforge/$NEO/neoforge-$NEO-installer.jar"
(cd $DIR && java -jar installer.jar --installServer > install.log 2>&1)
cp "$JAR" $DIR/mods/
echo "eula=true" > $DIR/eula.txt
printf "level-type=minecraft\\:flat\nonline-mode=false\nspawn-protection=0\n" > $DIR/server.properties
cd $DIR
mkfifo input
(java @user_jvm_args.txt @libraries/net/neoforged/neoforge/$NEO/unix_args.txt nogui < input > server.log 2>&1; echo "exit $?" >> server.log) &
exec 3> input
for i in $(seq 1 180); do
  if grep -q "Done (" server.log; then break; fi
  if grep -q "^exit " server.log; then break; fi
  sleep 2
done
echo stop >&3
for i in $(seq 1 60); do grep -q "^exit " server.log && break; sleep 2; done
exec 3>&-
grep -E "MineComputers|minepc|Exception|ERROR" server.log | head -40
grep -q "Lua (Cobalt) ready: 42" server.log || { echo "Движок Lua не загрузился в готовом моде"; exit 1; }
grep -q "Done (" server.log || { echo "Сервер не запустился"; tail -50 server.log; exit 1; }
echo "Готовый мод работает на сервере NeoForge $NEO"

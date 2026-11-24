#!/bin/sh
# Builds and starts the simulator (macOS, Linux, Git Bash). Needs a JDK 11 or newer.
#   sh run.sh                 demo values
#   sh run.sh --live          listen for OpenHaldex / tools/gauge-send.py on UDP 49162..49153
set -e
cd "$(dirname "$0")"
rm -rf out && mkdir -p out
javac -nowarn --release 8 -encoding UTF-8 -d out src/local/tt/*.java sim/local/tt/*.java
exec java -cp out local.tt.TeleSim "$@"

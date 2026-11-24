# Audi TT 8S autocross view simulator

A live PC simulator of the OpenHaldex autocross view that tt-zoom draws on the TT 8S virtual
cockpit (MIB2 MU1462). It runs the head unit's own layout code (`TeleLayout`, `TeleIcons`,
`TeleStrip`) and its UDP receiver (`TeleLink`), and draws the layout's slots in a window at the
head unit's tick rate, inside the stock cockpit drawn clean (rev counter and speedometer with
needles that follow the data, tab bar, bottom bar; in Sport the big rev counter and the on-board
computer card). The phone photos of the car are still there behind the B key.

The source of truth for `src/` is the altscreen repository, branch `telemetry`
(`ports/tt-8s-mu1462/hmi/src/local/tt`). `sh sync.sh [path]` copies those files, the photos and the
test sender here. Nothing from the car's firmware is in this repository.

## Run

Needs a JDK 11 or newer (`javac` and `java` on PATH).

```sh
sh run.sh                 # macOS, Linux, Git Bash: demo values
sh run.sh --live          # listen for OpenHaldex or tools/gauge-send.py
```

```powershell
.\run.cmd                 # Windows (or double-click run.cmd; works when scripts are disabled)
.\run.cmd --live
```

Options: `--sport` (Sport view), `--pieces` (the tele7 rev strip instead of the painted one),
`--glide`, `--photo` (start with the phone photo), `--hz 15|20|30|60` (tick rate, default 30 like the car), `--scale 0.9` (window size),
`--key N` (link key), `--shot SECONDS FILE.png` (save one frame and quit).

Keys in the window:

| Key | What |
|---|---|
| V | Classic / Sport view |
| P | Rev strip: painted (tele8 probe: two pictures, the lit one clipped at the rpm) or pieces (tele7) |
| D | Demo values / live UDP |
| G | Glide: move smoothly between data samples (one sample of delay) instead of jumping |
| R | Demo data rate 10 / 20 / 50 Hz (OpenHaldex sends about 20 Hz) |
| F | Tick rate 15 / 20 / 30 / 60 Hz |
| B | Background: stock cockpit drawn clean / phone photo / black |
| H | Readout on / off (tick rate, frames drawn, layout and draw time, link) |
| S | Save the frame as `sim-<time>.png` |

## Backgrounds

B cycles: **skin** (pieces of a real cockpit picture, layered: `skins/*.skin` says what to cut out
and where it goes on the car's screen; the pictures stay out of the repository, see
`skins/README.md`), **drawn** (the cockpit drawn by `Chrome.java`, used when a view has no skin),
**phone photo**, **black**. `skins/classic.skin` takes the dials, tab bar and bottom bar from the
owner's manual's 620 x 272 cockpit screenshot; `skins/sport.skin` the big rev counter, status rows, navigation panel and bottom bar from the manual's "Sport view (TTS)" figure, with its numbered marker painted over (`patch`).

## Live data

With `--live` the receiver binds the first free port of UDP 49162..49153, as on the head unit,
and acks every datagram, so the sender's statistics work. From the same PC:

```sh
python3 tools/gauge-send.py --host 127.0.0.1 --port 49162            # demo sweep, 20 Hz
python3 tools/gauge-send.py --host 127.0.0.1 --port 49162 --set rpm=6620 boost=110 gear=3
python3 tools/gauge-send.py --host 127.0.0.1 --port 49162 --presets  # also the preset catalog
```

OpenHaldex itself sends to the MMI hotspot's address (10.173.189.1); to feed the simulator from the
board, the board would have to send to the PC instead.

## What it does not show

- The head unit's font: about 1.45x larger at the same size there, so tt-zoom draws text at 70 %.
  Text widths here are close, not exact.
- EAL's own behaviour: tilt direction, premultiplied blending, how the clip rectangle behaves.
- The head unit's speed. Frame rates here are the PC's; the car logs its own in
  `/tmp/tt-rgd.log` (`tele stats`).

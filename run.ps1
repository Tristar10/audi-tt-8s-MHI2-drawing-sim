# Builds and starts the simulator on Windows (PowerShell). Needs a JDK 11 or newer on PATH.
#   .\run.ps1            demo values
#   .\run.ps1 --live     listen for OpenHaldex / tools\gauge-send.py on UDP 49162..49153
Set-Location $PSScriptRoot
if (Test-Path out) { Remove-Item -Recurse -Force out }
New-Item -ItemType Directory out | Out-Null
$src = @(Get-ChildItem src\local\tt\*.java) + @(Get-ChildItem sim\local\tt\*.java) | ForEach-Object { $_.FullName }
javac -nowarn --release 8 -encoding UTF-8 -d out $src
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
java -cp out local.tt.TeleSim @args

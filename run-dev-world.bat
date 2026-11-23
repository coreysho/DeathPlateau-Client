@echo off
title Death Plateau - DEV WORLD (World 2)
echo ============================================
echo  Death Plateau - DEV WORLD
echo.
echo  Staff only. Separate saves, separate
echo  database, separate world from live.
echo ============================================
echo.

rem WHY THIS EXISTS, AND WHY IT IS NOT run-dev-client.bat.
rem
rem Two different things are called "dev" and they are not related:
rem   run-dev-client.bat  the DIAGNOSTIC CLIENT build (build\libs\rs2client-dev.jar),
rem                       which connects to LIVE - its defaults are the relay on 43594.
rem   this script         the DEV WORLD (World 2), on the relay's 43595 / 8889.
rem
rem On 2026-11-14 the dev world could not be reached and an evening went into it. The cause was
rem rs2client-dev.jar, built 2 November, the day before ec38859 swapped the playit.gg tunnel
rem defaults for the Chicago relay. That jar still dials carolyn-sternness.tun.ply.gg:55662,
rem which has not existed since the playit subscription ended - and run-dev-client.bat passes no
rem host, so nothing on the machine could override it.
rem
rem So this script uses the LAUNCHER'S client, which updates itself, and names the ports rather
rem than trusting whatever a jar was built with. -D properties beat both the environment and the
rem compiled-in defaults (Client.setting checks getProperty before getenv), so this works whatever
rem state anything else is in.

set CLIENT=%USERPROFILE%\.deathplateau\client.jar
if not exist "%CLIENT%" (
    echo Could not find "%CLIENT%".
    echo Run the launcher once first - it downloads and keeps the client there.
    pause
    exit /b 1
)

rem The relay, and the dev world's OWN ports. The VPS forwards 43595 and 8889 unchanged; live is
rem 43594 / 8888. Duplicated from launcher Launcher.DEV_RELAY_* - if the relay ever moves, both
rem files change, and so does Client.RELAY_HOST.
java -Dlostcity.host=216.128.157.152 -Dlostcity.port=43595 -Dlostcity.webhost=216.128.157.152 -Dlostcity.webport=8889 -jar "%CLIENT%"

echo.
echo Client exited. Press any key to close this window.
pause >nul

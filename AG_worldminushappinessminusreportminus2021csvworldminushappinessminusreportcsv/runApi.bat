:; echo "running API in Mac"
:; DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
:; osascript -e "tell application \"Terminal\" to do script \"cd '$DIR';chmod u+x runApi2.bat;./runApi2.bat\""

echo "running API in Windows"
set mypath=%~dp0
echo %mypath%
cd %mypath%
start cmd /k runApi2.bat
exit

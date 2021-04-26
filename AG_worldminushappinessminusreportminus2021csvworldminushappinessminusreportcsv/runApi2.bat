:; echo "running API in mac"
:; DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null && pwd )"
:; echo $DIR
:; cd $DIR/apiCode
:; npm start

echo "running API in windows"
cd apiCode
npm install & npm audit fix & npm install https://github.com/cgmora12/swagger-tools --save & npm start

pause
set /P name=
java -jar bin\BF2OC.jar source\%name%.bf c-source\%name%.c 20000
gcc -o %name% c-source\%name%.c
PAUSE
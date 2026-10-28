set /P name=
java BfToC.java source\%name%.bf c-source\%name%.c
gcc -O0 -g -o %name% c-source\%name%.c
PAUSE
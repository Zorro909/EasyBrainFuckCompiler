set /P name=
java BfToC.java source\%name%.bf c-source\%name%.c
gcc -O1 -o %name% c-source\%name%.c
PAUSE
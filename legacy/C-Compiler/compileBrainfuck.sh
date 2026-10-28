#!/bin/sh
# Usage: compileBrainfuck.sh program.bf [tapeCells]  ->  ./program (in the current directory)
# Large inputs (millions of commands) take gcc minutes and gigabytes at -O1.
set -e
dir=$(dirname "$0")
name=$(basename "$1" .bf)
java "$dir/BfToC.java" "$1" "$dir/c-source/$name.c" ${2:+"$2"}
gcc -O1 -o "$name" "$dir/c-source/$name.c"

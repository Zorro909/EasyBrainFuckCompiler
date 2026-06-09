#include <stdio.h>
#include <stdlib.h>
int main(void) {
int array[20000]  = {0};
    int ptr = 0;
++ptr;
++ptr;
array[ptr]+=7;
while(array[ptr]){
--ptr;
array[ptr]+=10;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]+=5;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]-=6;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=2;
while(array[ptr]){
--ptr;
array[ptr]+=4;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
++array[ptr];
while(array[ptr]){
--ptr;
array[ptr]-=10;
array[ptr]-=10;
array[ptr]-=10;
array[ptr]-=10;
array[ptr]-=3;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=4;
while(array[ptr]){
--ptr;
array[ptr]+=10;
array[ptr]+=3;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]-=4;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
++array[ptr];
while(array[ptr]){
--ptr;
++array[ptr];
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=2;
while(array[ptr]){
--ptr;
array[ptr]+=5;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]-=10;
array[ptr]-=7;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]+=10;
array[ptr]+=7;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
++array[ptr];
while(array[ptr]){
--ptr;
array[ptr]-=10;
--array[ptr];
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
++array[ptr];
while(array[ptr]){
--ptr;
++array[ptr];
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
++array[ptr];
while(array[ptr]){
--ptr;
array[ptr]+=10;
++array[ptr];
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]-=10;
array[ptr]-=7;
++ptr;
--array[ptr];
}
--ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
printf("%c",array[ptr]); fflush(stdout);
--ptr;
++ptr;
++ptr;
array[ptr]+=3;
while(array[ptr]){
--ptr;
array[ptr]-=10;
--array[ptr];
++ptr;
--array[ptr];
}
--ptr;
--ptr;
return 0;
}
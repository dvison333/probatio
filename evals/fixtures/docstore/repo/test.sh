#!/bin/sh
set -e
rm -rf out
javac -d out src/*.java test/*.java
java -cp out RunTests

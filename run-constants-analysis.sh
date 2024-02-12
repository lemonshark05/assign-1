#!/bin/bash

set -e

# $1 = lir program file; $2 = json format file; $3 = function to analyze
./my_binary "$1" "$2" "$3"

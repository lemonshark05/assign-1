#!/bin/bash

if [ "$#" -lt 1 ]; then
    echo "Usage: ./run-interval-analysis.sh <lir_file> [json_file]"
    exit 1
fi

LIR_FILE="$1"

if [ -f "$LIR_FILE" ]; then
    javac DataFlowInterval.java
    java DataFlowInterval "$LIR_FILE"

elif [ -f "$JSON_FILE" ]; then
  pass
#    python read_json.py "$JSON_FILE"
else
    # If neither file exists, output an error message
    echo "Error: Neither LIR nor JSON file exists."
    exit 1
fi

#!/bin/bash

#set -e
#
## $1 = lir program file; $2 = json format file; $3 = function to analyze
#./my_binary "$1" "$2" "$3"

#!/bin/bash

if [ "$#" -lt 1 ]; then
    echo "Usage: ./run-constants-analysis.sh <lir_file> [json_file]"
    exit 1
fi

LIR_FILE="$1"

if [ -f "$LIR_FILE" ]; then
    javac DataFlowConstants.java
    java DataFlowConstants "$LIR_FILE"
#    javac dataFlowInterval.java
#    java dataFlowInterval "$LIR_FILE"

elif [ -f "$JSON_FILE" ]; then
  pass
#    python read_json.py "$JSON_FILE"
else
    # If neither file exists, output an error message
    echo "Error: Neither LIR nor JSON file exists."
    exit 1
fi
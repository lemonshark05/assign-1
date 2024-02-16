#!/bin/bash

TEST_DIR="./complex"
OUTPUT_DIR="my-results"

mkdir -p "$OUTPUT_DIR"

# Loop through each .lir file in the directory
for file in "$TEST_DIR"/*.lir; do
    filename=$(basename "${file%.lir}")
    output_dir="$OUTPUT_DIR/$filename"

    mkdir -p "$output_dir"

    json_file="$output_dir/${filename}.lir.json"
    stats_file="${file%.lir}.constants.soln"
    output_stats="$output_dir/my-$filename"
    diff_output="$output_dir/diff.txt"

    ./run-constants-analysis.sh "$file" "$json_file" > "$output_stats"
    diff -wp "$output_stats" "$stats_file" > "$diff_output"

    if [ -s "$diff_output" ]; then
        echo "Processed $file - NOT PASS >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>"
    else
        echo "Processed $file - pass"
    fi
done
import sys
import json

def parse_lir_file(file_path):
    with open(file_path, 'r') as file:
        data = json.load(file)  # Load JSON data from file

        # Lists to hold detailed information
        globals_info = []
        locals_info = []

        # Process globals
        for global_var in data['globals']:
            if "Pointer" in global_var['typ'] and "Function" in global_var['typ']['Pointer']:
                globals_info.append(global_var['name'])

        # Process locals within functions
        for function_name, function_details in data['functions'].items():
            for local in function_details['locals']:
                if "Pointer" in local['typ'] and "Function" in local['typ']['Pointer']:
                    locals_info.append((function_name, local['name']))

        # Print detailed information
        print("Globals with pointer to function type:")
        for name in globals_info:
            print(f" - {name}")

        print("\nLocals with pointer to function type:")
        for func_name, local_name in locals_info:
            print(f" - Function '{func_name}' has local variable '{local_name}'")

        # Print summary
        print(f"\nTotal globals with pointer to function type: {len(globals_info)}")
        print(f"Total locals with pointer to function type: {len(locals_info)}")
        print(f"Total combined: {len(globals_info) + len(locals_info)}")

if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage: python3 run1.py <json_file_path>")
        sys.exit(1)

    json_file_path = sys.argv[1]
    parse_lir_file(json_file_path)

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LirParser {

    static int numStructFields = 0;
    static int numFunctionsWithReturn = 0;
    static int numFunctionParameters = 0;
    static int numLocalVariables = 0;
    static int numBasicBlocks = 0;
    static int numInstructions = 0;
    static int numTerminals = 0;
    static int numLocalsAndGlobalsIntType = 0;
    static int numLocalsAndGlobalsStructType = 0;
    static int numLocalsAndGlobalsPointerIntType = 0;
    static int numLocalsAndGlobalsPointerStructType = 0;
    static int numLocalsAndGlobalsPointerFunctionType = 0;
    static int numLocalsAndGlobalsPointerPointerType = 0;

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java LirParser <lir_file_path>");
            System.exit(1);
        }

        String lirFilePath = args[0];
        parseLirFile(lirFilePath);
        printStatistics();
    }

    private static void parseLirFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            boolean isStruct = false;
            boolean isFunction = false;
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith("struct ")) {
                    isStruct = true;
                } else if (isStruct && line.equals("}")) {
                    isStruct = false;
                } else if (isStruct) {
                    parseStruct(line);
                } else if (line.startsWith("fn ")) {
                    isFunction = true;
                    parseFunction(line);
                } else if (isFunction && line.startsWith("}")) {
                    isFunction = false;
                } else if (isFunction) {
                    parseFunctionContent(line);
                } else if (line.startsWith("extern ")) {
                    parseExternFunction(line);
                } else if (!isFunction && !isStruct && line.contains(":")) {
                    parseGlobalLine(line);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void parseStruct(String line) {
        Matcher fieldMatcher = Pattern.compile("\\s*(\\w+):(.+)").matcher(line);
        if (fieldMatcher.matches()) {
            numStructFields++;
        }
    }

    private static void parseExternFunction(String line) {
        // Example: Implementation could parse extern functions, if needed
    }

    private static String replaceCommas(String inputString, char oldChar, char newChar) {
        int level = 0;
        StringBuilder output = new StringBuilder();

        for (char c : inputString.toCharArray()) {
            if (c == '(') {
                level++;
            } else if (c == ')') {
                level--;
            } else if (c == oldChar && level > 0) {
                c = newChar;
            }
            output.append(c);
        }

        return output.toString();
    }

    private static void parseFunctionContent(String line) {
        Matcher localVarMatcher = Pattern.compile("let\\s+(.*)").matcher(line);
        Pattern instructionPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp)");
        Pattern terminalPattern = Pattern.compile("\\$(branch|jump|ret|call_dir|call_idr)");

        if (localVarMatcher.find()) {
            String allVars = replaceCommas(localVarMatcher.group(1), ',', '|');
            for (String var : allVars.split(",")) {
                if (var.contains(":")) {
                    numLocalVariables++;
                    String varType = var.split(":")[1].trim();
                    countVariableTypes(varType);
                }
            }
        }

        if (line.matches("^\\w+:")) {
            numBasicBlocks++;
        }

        if (instructionPattern.matcher(line).find()) {
            numInstructions++;
        }

        if (terminalPattern.matcher(line).find()) {
            numTerminals++;
        }
    }

    private static void parseFunction(String line) {
        if (line.contains("->")) {
            if (!line.contains("-> _")) {
                numFunctionsWithReturn++;
            }

            String params = line.split("\\(")[1].split("\\)")[0];
            if (!params.isEmpty()) {
                numFunctionParameters += params.split(",").length;
            }
        }
    }

    private static void parseGlobalLine(String line) {
        Matcher globalLineMatcher = Pattern.compile("(\\w+):\\s*(&?[\\w&() ->]+)").matcher(line);
        if (globalLineMatcher.matches()) {
            String varType = globalLineMatcher.group(2);
            countVariableTypes(varType);
        }
    }

    private static void countVariableTypes(String varType) {
        if (varType.equals("int")) {
            numLocalsAndGlobalsIntType++;
        } else if (varType.startsWith("struct")) {
            numLocalsAndGlobalsStructType++;
        } else if (varType.startsWith("&")) {
            if (varType.startsWith("&int")) {
                numLocalsAndGlobalsPointerIntType++;
            } else if (varType.startsWith("&struct")) {
                numLocalsAndGlobalsPointerStructType++;
            } else if (varType.startsWith("&&")) {
                numLocalsAndGlobalsPointerPointerType++;
            } else if (varType.startsWith("&(") && varType.contains("->")) {
                numLocalsAndGlobalsPointerFunctionType++;
            }
        }
    }

    private static void printStatistics() {
        System.out.println("Number of fields across all struct types: " + numStructFields);
        System.out.println("Number of functions that return a value: " + numFunctionsWithReturn);
        System.out.println("Number of function parameters: " + numFunctionParameters);
        System.out.println("Number of local variables: " + numLocalVariables);
        System.out.println("Number of basic blocks: " + numBasicBlocks);
        System.out.println("Number of instructions: " + numInstructions);
        System.out.println("Number of terminals: " + numTerminals);
        System.out.println("Number of locals and globals with int type: " + numLocalsAndGlobalsIntType);
        System.out.println("Number of locals and globals with struct type: " + numLocalsAndGlobalsStructType);
        System.out.println("Number of locals and globals with pointer to int type: " + numLocalsAndGlobalsPointerIntType);
        System.out.println("Number of locals and globals with pointer to struct type: " + numLocalsAndGlobalsPointerStructType);
        System.out.println("Number of locals and globals with pointer to function type: " + numLocalsAndGlobalsPointerFunctionType);
        System.out.println("Number of locals and globals with pointer to pointer type: " + numLocalsAndGlobalsPointerPointerType);
    }
}
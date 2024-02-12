import jdk.dynalink.Operation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataFlowConstants {

    static class Operation {
        String block;
        String instruction;

        Operation(String block, String instruction) {
            this.block = block;
            this.instruction = instruction;
        }
    }

    public static void dataFlow(String filePath) {
        Map<String, List<Operation>> basicBlocks = new HashMap<>();
        Map<String, VariableState> variableStates = new HashMap<>();
        Map<String, List<String>> blockSuccessors = new HashMap<>();
        Queue<Operation> worklist = new LinkedList<>();
        Map<String, VariableState> preStates = new HashMap<>();
        Map<String, VariableState> postStates = new HashMap<>();

        parseLirFile(filePath, basicBlocks, variableStates, worklist, blockSuccessors);
        // Initialize pre-states and post-states for all blocks to bottom (⊥)
        basicBlocks.keySet().forEach(blockName -> {
            preStates.put(blockName, new VariableState()); // Assuming bottom state is default
            postStates.put(blockName, new VariableState());
        });

        while (!worklist.isEmpty()) {
            Operation operation = worklist.poll();
            String block = operation.block;
            VariableState preState = computePreState(block, preStates, postStates, blockSuccessors);
            VariableState oldPostState = postStates.get(block) != null ? postStates.get(block).clone() : new VariableState();
            VariableState newPostState = analyzeInstruction(operation, preState, variableStates);

            if (!newPostState.equals(oldPostState)) {
                postStates.put(block, newPostState);
                blockSuccessors.getOrDefault(block, new ArrayList<>()).forEach(successor -> {
                    basicBlocks.get(successor).forEach(op -> worklist.offer(op)); // Re-enqueue operations for analysis
                });
            }
        }
        printAnalysisResults(basicBlocks, variableStates);
    }

    private static VariableState computePreState(String block, Map<String, VariableState> preStates, Map<String, VariableState> postStates, Map<String, List<String>> blockSuccessors) {
        // Compute pre-state logic here
        return new VariableState();
    }


    private static VariableState analyzeInstruction(Operation operation, VariableState currentState, Map<String, VariableState> variableStates) {
        String instruction = operation.instruction;
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|branch|jump|ret|call_dir|call_idr)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String leftVar = parts[0];
        if (matcher.find()) {
            String opera = matcher.group(1);
            switch (opera) {
                case "store":
                    if (parts.length > 2) {
                        String pointerVar = parts[1];
                        String value = parts[2];
                        VariableState pointerState = variableStates.get(pointerVar);
                        if (pointerState != null && pointerState.getPointsTo() != null) {
                            String targetVar = pointerState.getPointsTo();
                            VariableState targetVarState = variableStates.get(targetVar);
                            if (targetVarState != null) {
                                VariableState newValueState = new VariableState();
                                newValueState.isTop = true;
                                targetVarState.weakUpdate(newValueState);
                            }
                        } else {

                        }
                    }
                    break;
                case "load":
                    if (variableStates.containsKey(leftVar)) {
                        VariableState loadedState = variableStates.get(leftVar);
                        currentState.merge(loadedState);
                    }
                    break;
                case "alloc":
                    variableStates.put(leftVar, new VariableState());
                    break;
                case "cmp":
                case "arith":
                    currentState.markAsTop();
                    break;
                case "gep":
                    break;
                case "copy":
                    if (parts.length > 3) {
                        String copiedVar = parts[3];
                        VariableState copiedState = variableStates.get(copiedVar);
                        if (copiedState != null && copiedState.getPointsTo() != null) {
                            variableStates.get(leftVar).setPointsTo(copiedState.getPointsTo());
                        }
                    }
                    break;
                case "call_ext":
                case "call_dir":
                    if (instruction.contains("then")) {
                        String targetBlock = instruction.substring(instruction.lastIndexOf("then") + 5).trim();
                    }
                    break;
                case "call_idr":
                    break;
                case "addrof":
                    if (parts.length > 2) {
                        String pointedVar = parts[3];
                        variableStates.get(leftVar).setPointsTo(pointedVar);
                    }
                    break;
                case "gfp":
                    break;
                case "branch":
                case "jump":
                    break;
                case "ret":
                    if (parts.length > 1) {
                        String retVar = parts[1];
                        currentState.markAsTop();
                    }
                    break;
                default:
                    break;
            }
        }
        return currentState;
    }


    private static void parseLirFile(String filePath, Map<String, List<Operation>> basicBlocks, Map<String, VariableState> variableStates, Queue<Operation> worklist, Map<String, List<String>> blockSuccessors) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            List<String> instructions = new ArrayList<>();
            boolean isFunction = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("fn ")) {
                    isFunction = true;
                } else if (isFunction && line.startsWith("}")) {
                    isFunction = false;
                    currentBlock = null;
                } else if (isFunction) {
                    if (line.matches("^\\w+:")) {
                        currentBlock = line.replace(":", "");
                        basicBlocks.putIfAbsent(currentBlock, new ArrayList<>());
                    } else if (line.startsWith("let ")) {
                        String variablesPart = line.substring("let ".length());
                        String[] variables = variablesPart.split(",\\s*");
                        for (String varDeclaration : variables) {
                            String varName = varDeclaration.split(":")[0].trim();
                            variableStates.put(varName, new VariableState());
                        }
                    } else {
                        if (currentBlock != null) {
                            Operation newOp = new Operation(currentBlock, line);
                            basicBlocks.get(currentBlock).add(newOp);
                            worklist.add(newOp);
                            if (line.startsWith("$branch") || line.startsWith("$jump")) {
                                String targetBlock = extractTargetBlock(line);
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(targetBlock);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static String extractTargetBlock(String instruction) {
        Pattern pattern = Pattern.compile("\\$(branch|jump)\\s+(\\w+)");
        Matcher matcher = pattern.matcher(instruction);
        if (matcher.find()) {
            return matcher.group(2);
        }
        return "";
    }
    private static void parseFunctionContent(String line, Map<String, VariableState> variableStates, Queue<Operation> worklist, String currentBlock) {
        if (line.startsWith("let ")) {
            String variablesPart = line.substring(4);
            String[] variables = variablesPart.split(",");

            for (String var : variables) {
                String varName = var.split(":")[0].trim(); // Get the variable name before the colon
                variableStates.put(varName, new VariableState()); // Initialize and put it in the map
            }
        } else {
            worklist.add(new Operation(currentBlock, line));
        }
    }

    private static void printAnalysisResults(Map<String, List<Operation>> basicBlocks, Map<String, VariableState> variableStates) {
        basicBlocks.forEach((blockName, instructions) -> {
            System.out.println(blockName + ":");
            variableStates.forEach((varName, state) -> {
                if (state.isTop) {
                    System.out.println(varName + " -> Top");
                }
            });
        });
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java DataFlowConstants <lir_file_path>");
            System.exit(1);
        }
        String lirFilePath = args[0];
        dataFlow(lirFilePath);
    }
}
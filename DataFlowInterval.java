import jdk.dynalink.Operation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DataFlowInterval {

    static class Operation {
        String block;
        String instruction;

        Operation(String block, String instruction) {
            this.block = block;
            this.instruction = instruction;
        }
    }

    static Set<String> addressTakenVariables = new HashSet<>();
    static Set<String> globalVariables = new HashSet<>();

    public static void dataFlow(String filePath) {
        Map<String, List<Operation>> basicBlocks = new TreeMap<>();
        Map<String, VariableState> variableStates = new TreeMap<>();
        Map<String, List<String>> blockSuccessors = new HashMap<>();
        Map<String, TreeSet<String>> blockVars = new HashMap<>();
        Queue<Operation> worklist = new LinkedList<>();
        Map<String, VariableState> preStates = new HashMap<>();
        Map<String, VariableState> postStates = new HashMap<>();

        parseLirFile(filePath, basicBlocks, variableStates, worklist, blockSuccessors);
        // Initialize all blocks to bottom (⊥)
        basicBlocks.keySet().forEach(blockName -> {
            blockVars.put(blockName, new TreeSet<>());
            preStates.put(blockName, new VariableState());
            postStates.put(blockName, new VariableState());
        });

        while (!worklist.isEmpty()) {
            Operation operation = worklist.poll();
            String block = operation.block;
            VariableState preState = computePreState(block, preStates, postStates, blockSuccessors);
            VariableState oldPostState = postStates.get(block) != null ? postStates.get(block).clone() : new VariableState();
            VariableState newPostState = analyzeInstruction(operation, preState, variableStates, blockVars);

            if (!newPostState.equals(oldPostState)) {
                List<String> successors = blockSuccessors.getOrDefault(block, new ArrayList<>());
                for (String successor : successors) {
                    List<Operation> operationsInSuccessor = basicBlocks.get(successor);
                    if (operationsInSuccessor != null) {
                        for (Operation opera : operationsInSuccessor) {
                            worklist.offer(opera);
                        }
                    }
                }
            }
        }
        printAnalysisResults(basicBlocks, variableStates, blockVars);
    }

    private static VariableState computePreState(String block, Map<String, VariableState> preStates, Map<String, VariableState> postStates, Map<String, List<String>> blockPredecessors) {
        VariableState preState = new VariableState();
        List<String> predecessors = blockPredecessors.get(block);

        if (predecessors != null) {
            for (String pred : predecessors) {
                VariableState predPostState = postStates.get(pred);

                if (predPostState != null) {
                    preState = preState.join(predPostState);
                } else {
                    preState.markAsTop();
                    break;
                }
            }
        }
        return preState;
    }

    private static VariableState analyzeInstruction(Operation operation, VariableState currentState, Map<String, VariableState> variableStates, Map<String, TreeSet<String>> blockVars) {
        String instruction = operation.instruction;
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|branch|jump|ret|call_dir|call_idr)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String leftVar = parts[0];
        TreeSet<String> varsInBlock = blockVars.get(operation.block);

        if (matcher.find()) {
            String opera = matcher.group(1);
            for (int i = 1; i < parts.length; i++) {
                String part = parts[i];
                if (variableStates.containsKey(part) && variableStates.get(part).isInt()) {
                    varsInBlock.add(part);
                }
            }
            switch (opera) {
                case "store":
                    String pointerVar = parts[1];
                    String valueVarOrConstant = parts[2];
                    if (valueVarOrConstant.matches("\\d+")) {
                        if (variableStates.containsKey(pointerVar) && variableStates.get(pointerVar).getPointsTo() != null) {
                            String pointedVar = variableStates.get(pointerVar).getPointsTo();
                            if (addressTakenVariables.contains(pointedVar) || globalVariables.contains(pointedVar)) {
                                VariableState newStates = variableStates.get(pointedVar);
                                variableStates.get(pointedVar).setConstantValue(Integer.parseInt(valueVarOrConstant));
                            }
                        }
                    } else if (variableStates.containsKey(valueVarOrConstant)) {
                        VariableState valueState = variableStates.get(valueVarOrConstant);
                        if (variableStates.containsKey(pointerVar) && variableStates.get(pointerVar).getPointsTo() != null) {
                            String pointedVar = variableStates.get(pointerVar).getPointsTo();
                            if (addressTakenVariables.contains(pointedVar)) {
                                variableStates.get(pointedVar).weakUpdate(valueState);
                            }
                        }
                    }
                    break;
                case "load":
                    if (variableStates.containsKey(leftVar)) {
                        VariableState loadedState = variableStates.get(leftVar);
                        currentState.merge(loadedState);
                        loadedState.markAsTop();
                    }
                    break;
                case "alloc":
                    break;
                case "cmp":
                    handleCmp(parts, leftVar, variableStates);
                case "arith":
                    handleArith(parts, leftVar, variableStates);
                    break;
                case "gep":
                    break;
                case "copy":
                    if (parts.length > 3) {
                        String copiedVar = parts[3];
                        VariableState updateVar = variableStates.get(leftVar);
                        VariableState copiedState = variableStates.get(copiedVar);
                        if (copiedState != null) {
                            if (copiedState.getPointsTo() != null) {
                                updateVar.setPointsTo(copiedState.getPointsTo());
                            } else if (copiedState.isInt() && copiedState.hasConstantValue()) {
                                updateVar.setConstantValue(copiedState.getConstantValue());
                            } else {
                                updateVar.markAsTop();
                            }
                        } else {
                            try {
                                int value = Integer.parseInt(copiedVar);
                                updateVar.setConstantValue(value);
                            } catch (NumberFormatException e) {
                                updateVar.markAsTop();
                            }
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
                    for (String global : globalVariables) {
                        variableStates.get(global).markAsTop();
                    }
                    break;
                case "addrof":
                    if (parts.length > 2) {
                        String pointedVar = parts[3];
                        variableStates.get(leftVar).setPointsTo(pointedVar);
                    }
                    break;
                case "gfp":
                    if (variableStates.containsKey(leftVar)) {
                        variableStates.get(leftVar).markAsTop();
                    } else {
                        VariableState newTopState = new VariableState();
                        newTopState.markAsTop();
                        variableStates.put(leftVar, newTopState);
                    }
                    break;
                case "branch":
                case "jump":
                    break;
                case "ret":
                    break;
                default:
                    break;
            }
        }
        return currentState;
    }
    private static void handleArith(String[] parts, String leftVar, Map<String, VariableState> variableStates) {
        if (parts.length < 5) return;

        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        Integer result = null;
        boolean isTop = false;

        Integer value1 = null, value2 = null;
        VariableState operand1State = null, operand2State = null;

        try {
            value1 = Integer.parseInt(operand1);
        } catch (NumberFormatException e) {
            operand1State = variableStates.get(operand1);
            if (operand1State != null && operand1State.hasConstantValue()) {
                value1 = operand1State.getConstantValue();
            } else if (operand1State != null && operand1State.isTop()) {
                isTop = true;
            }
        }

        try {
            value2 = Integer.parseInt(operand2);
        } catch (NumberFormatException e) {
            operand2State = variableStates.get(operand2);
            if (operand2State != null && operand2State.hasConstantValue()) {
                value2 = operand2State.getConstantValue();
            } else if (operand2State != null && operand2State.isTop()) {
                isTop = true;
            }
        }

        VariableState leftState = variableStates.get(leftVar);

        if (isTop) {
            leftState.markAsTop();
        } else if (value1 != null && value2 != null) {
            result = performArithmetic(operation, value1, value2);
            if (result != null) {
                leftState.setConstantValue(result);
            } else {
                leftState.markAsTop();
            }
        } else {

        }
    }

    private static void handleCmp(String[] parts, String leftVar, Map<String, VariableState> variableStates) {
        if (parts.length < 5) return;

        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        boolean result = false;
        boolean isTop = false;

        Integer value1 = null, value2 = null;
        VariableState operand1State = null, operand2State = null;

        try {
            value1 = Integer.parseInt(operand1);
        } catch (NumberFormatException e) {
            isTop = true;
        }

        try {
            value2 = Integer.parseInt(operand2);
        } catch (NumberFormatException e) {
            isTop = true;
        }

        VariableState leftState = variableStates.get(leftVar);

        if (isTop) {
            leftState.markAsTop();
        } else if (value1 != null && value2 != null) {
            result = performComparison(operation, value1, value2);
            leftState.markAsTop();
        }
    }

    private static void parseLirFile(String filePath, Map<String, List<Operation>> basicBlocks, Map<String, VariableState> variableStates, Queue<Operation> worklist, Map<String, List<String>> blockSuccessors) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
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
                            String[] parts = varDeclaration.split(":");
                            String varName = parts[0].trim();
                            // just get int type
                            String type = parts[1].trim();
                            VariableState newState = new VariableState();
                            if (type.startsWith("&")) {
                                newState.setPointsTo(type.substring(1));
                            }else if(type.equals("int")){
                                newState.setInt(true);
                            }
                            globalVariables.add(varName);
                            variableStates.put(varName, newState);
                        }
                    } else if (line.contains("$addrof")) {
                        String[] parts = line.split(" ");
                        if (parts.length > 3) {
                            String address = parts[0];
                            String pointTo = parts[3];
                            variableStates.get(address).setPointsTo(pointTo);
                            String addressTakenVar = parts[3];
                            addressTakenVariables.add(addressTakenVar);
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

    private static Integer performArithmetic(String op, Integer val1, Integer val2) {
        switch (op) {
            case "add":
                return val1 + val2;
            case "sub":
                return val1 - val2;
            case "mul":
                return val1 * val2;
            case "div":
                if (val2 == 0) return null;
                return val1 / val2;
            default:
                return null;
        }
    }
    private static Boolean performComparison(String op, Integer val1, Integer val2) {
        switch (op) {
            case "eq":
                return Objects.equals(val1, val2);
            case "neq":
                return !Objects.equals(val1, val2);
            case "lt":
                return val1 < val2;
            case "lte":
                return val1 <= val2;
            case "gt":
                return val1 > val2;
            case "gte":
                return val1 >= val2;
            default:
                return null;
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

    private static void printAnalysisResults(Map<String, List<Operation>> basicBlocks, Map<String, VariableState> variableStates, Map<String, TreeSet<String>> blockVars) {
        // Sort the basic block names alphabetically
        for (String blockName : basicBlocks.keySet()) {
            System.out.println(blockName + ":");
            TreeSet<String> varsInBlock = blockVars.getOrDefault(blockName, new TreeSet<>());
            for (String varName : varsInBlock) {
                VariableState state = variableStates.get(varName);
                System.out.println(varName + " -> " + (state.isTop() ? "Top" : state.getConstantValue()));
            }
            System.out.println();
        }
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
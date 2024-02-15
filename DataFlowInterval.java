import javax.swing.*;
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

    static Map<String, List<Operation>> basicBlocks = new HashMap<>();
    static Map<String, List<String>> blockSuccessors = new HashMap<>();

    static Map<String, TreeMap<String, String>> blockVars = new HashMap<>();
    static Map<String, VariableState> variableStates = new TreeMap<>();
    static Set<String> processedBlocks = new HashSet<>();

    static Queue<String> worklist = new LinkedList<>();


    public static void dataFlow(String filePath) {
        TreeMap<String, TreeMap<String, VariableState>> preStates = new TreeMap<>();
        TreeMap<String, TreeMap<String, VariableState>> postStates = new TreeMap<>();
        parseLirFile(filePath);
        for (String blockName : blockVars.keySet()) {
            TreeMap<String, VariableState> initialStates = new TreeMap<>();
            TreeMap<String, String> varsInBlock = blockVars.get(blockName);
            for (Map.Entry<String, String> entry : varsInBlock.entrySet()) {
                String varName = entry.getKey();
                VariableState newState = variableStates.get(varName).clone();
                initialStates.put(varName, newState);
            }
            preStates.put(blockName, initialStates);
        }

        worklist.add("entry");
        processedBlocks.add("entry");

        while (!worklist.isEmpty()) {
            String block = worklist.poll();
//            System.out.println("Poll Worklist: " + worklist.toString());
            TreeMap<String, VariableState> preState = preStates.get(block);
            TreeMap<String, VariableState> postState = analyzeBlock(block, preState, processedBlocks);
            postStates.put(block, postState);

            for (String successor : blockSuccessors.getOrDefault(block, new LinkedList<>())) {
                TreeMap<String, VariableState> successorPreState = preStates.get(successor);
                TreeMap<String, VariableState> joinedState = joinMaps(successorPreState, postState);
                if (!joinedState.equals(successorPreState)) {
                    preStates.put(successor, joinedState);
                    if (!worklist.contains(successor)) {
                        processedBlocks.add(successor);
                        worklist.add(successor);
//                        System.out.println("Add to Worklist: " + worklist.toString());
                    }
                }
            }
        }
//        printAnalysisResults(processedBlocks, variableStates, blockVars);
        printAnalysisResults(processedBlocks, preStates);
    }

    private static TreeMap<String, VariableState> analyzeBlock(String block, TreeMap<String, VariableState> preState, Set<String> processedBlocks) {
        TreeMap<String, VariableState> postState = new TreeMap<>(preState);
        for (Operation operation : basicBlocks.get(block)) {
            analyzeInstruction(postState, processedBlocks ,operation);
        }
        return postState;
    }

    private static TreeMap<String, VariableState> joinMaps(TreeMap<String, VariableState> map1, TreeMap<String, VariableState> map2) {
        TreeMap<String, VariableState> result = new TreeMap<>(map1);

        for (Map.Entry<String, VariableState> entry : map2.entrySet()) {
            String varName = entry.getKey();
            VariableState stateFromMap2 = entry.getValue();
            if (result.containsKey(varName)) {
                VariableState stateFromMap1 = result.get(varName);
                VariableState mergedState = stateFromMap1.join(stateFromMap2);
                result.put(varName, mergedState);
//                System.out.println("Merging state for variable '" + varName + "': " + stateFromMap1 + " ⊔ " + stateFromMap2 + " = " + mergedState);
            } else {
                result.put(varName, stateFromMap2);
//                System.out.println("Adding new state for variable '" + varName + "': " + stateFromMap2);
            }
        }

        return result;
    }

    private static void analyzeInstruction(TreeMap<String, VariableState> postState, Set<String> processedBlocks, Operation operation) {
        String instruction = operation.instruction;
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|ret|call_dir|call_idr|jump|branch)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String leftVar = parts[0];
        if (matcher.find()) {
            String opera = matcher.group(1);
            switch (opera) {
                case "store":
                    String pointerVar = parts[1];
                    String valueVarOrConstant = parts[2];
                    if (valueVarOrConstant.matches("\\d+")) {
                        if (variableStates.containsKey(pointerVar) && variableStates.get(pointerVar).getPointsTo() != null) {
                            String pointedVar = variableStates.get(pointerVar).getPointsTo();
                            if (addressTakenVariables.contains(pointedVar) || globalVariables.contains(pointedVar)) {
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
                    if (postState.containsKey(leftVar)) {
                        VariableState loadedState = postState.get(leftVar);
                        loadedState.markAsTop();
                    }
                    break;
                case "alloc":
                    break;
                case "cmp":
                    handleCmp(parts, leftVar, postState);
                    break;
                case "arith":
                    handleArith(parts, leftVar, postState);
                    break;
                case "gep":
                    break;
                case "copy":
                    if (parts.length > 3) {
                        String copiedVar = parts[3];
                        VariableState updateVar = postState.get(leftVar);
                        VariableState copiedState = postState.get(copiedVar);
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
                                if(updateVar!=null) {
                                    updateVar.markAsTop();
                                }
                            }
                        }
                    }
                    break;
                case "call_ext":

                    break;
                case "call_dir":
                    if(postState.get(leftVar)!=null) {
                        postState.get(leftVar).markAsTop();
                    }
                    if (instruction.contains("then")) {
                        String targetBlock = instruction.substring(instruction.lastIndexOf("then") + 5).trim();
                        worklist.add(targetBlock);
                        processedBlocks.add(targetBlock);
                        updateBlockVars(operation.block, variableStates, blockVars);
                        propagateVars(targetBlock, blockVars, operation.block);
                    }
                    break;
                case "call_idr":
                    for (String global : globalVariables) {
                        postState.get(global).markAsTop();
                    }
                    break;
                case "addrof":
                    if (parts.length > 2) {
                        String pointedVar = parts[3];
                        variableStates.get(leftVar).setPointsTo(pointedVar);
                        postState.get(pointedVar).markAsTop();
                    }
                    break;
                case "gfp":
                    if (postState.containsKey(leftVar)) {
                        postState.get(leftVar).markAsTop();
                    }
                    break;
                case "jump":
                    String targetBlockJump = extractTargetBlock(instruction);
                    updateBlockVars(operation.block, variableStates, blockVars);
                    propagateVars(targetBlockJump, blockVars, operation.block);
                    break;
                case "branch":
                    String condition = parts[1];
                    String trueTarget = parts[2];
                    String falseTarget = parts[3];
                    List<String> newSuccessor = new ArrayList<>();
                    try {
                        int conValue = Integer.parseInt(condition);
                        if (conValue != 0) {
                            newSuccessor.add(trueTarget);
                            blockSuccessors.put(operation.block, newSuccessor);
                            propagateVars(trueTarget, blockVars, operation.block);
                        } else{
                            newSuccessor.add(falseTarget);
                            blockSuccessors.put(operation.block, newSuccessor);
                            propagateVars(falseTarget, blockVars, operation.block);
                        }
                    } catch (NumberFormatException e) {
                        VariableState conditionVar = postState.get(condition);
                        if(conditionVar != null) {
                            if (conditionVar.isBottom()) {
                                blockSuccessors.put(operation.block, newSuccessor);
                                break;
                            } else if(conditionVar.hasConstantValue() && conditionVar.getConstantValue() != 0) {
                                newSuccessor.add(trueTarget);
                                blockSuccessors.put(operation.block, newSuccessor);
                                propagateVars(trueTarget, blockVars, operation.block);
                            } else if (conditionVar.hasConstantValue() && conditionVar.getConstantValue() == 0) {
                                newSuccessor.add(falseTarget);
                                blockSuccessors.put(operation.block, newSuccessor);
                                propagateVars(falseTarget, blockVars, operation.block);
                            } else {
                                propagateVars(trueTarget, blockVars, operation.block);
                                propagateVars(falseTarget, blockVars, operation.block);
                            }
                        }
                    }
                    break;
                case "ret":
                    break;
                default:
                    break;
            }
        }
    }

    private static void updateBlockVars(String blockName, Map<String, VariableState> variableStates, Map<String, TreeMap<String, String>> blockVars) {
        TreeMap<String, String> bvars = blockVars.computeIfAbsent(blockName, k -> new TreeMap<>());
        for (Map.Entry<String, VariableState> entry : variableStates.entrySet()) {
            String varName = entry.getKey();
            VariableState varState = entry.getValue();
            String valueRepresentation = varState.isTop() ? "Top" : String.valueOf(varState.getConstantValue());
            if (bvars.containsKey(varName)) {
                bvars.put(varName, valueRepresentation);
            }
        }
    }

    private static void propagateVars(String targetBlock, Map<String, TreeMap<String, String>> blockVars, String orginBlock) {
        TreeMap<String, String> targetVars = blockVars.get(targetBlock);
        TreeMap<String, String> originVars = blockVars.get(orginBlock);

        for (Map.Entry<String, String> entry : originVars.entrySet()) {
            targetVars.putIfAbsent(entry.getKey(), entry.getValue());
        }
        blockVars.put(targetBlock, targetVars);
    }

    private static VariableState getAbstractValue(String operand, Map<String, VariableState> postStates) {
        VariableState state =new VariableState();
        try {
            int value = Integer.parseInt(operand);
            state.setConstantValue(value);
        } catch (NumberFormatException e) {
            // Not an integer, so it should be a variable name
            if(postStates.containsKey(operand)) {
                state = postStates.get(operand);
            }else{
                state = variableStates.get(operand);
            }
        }
        return state;
    }

    private static void handleArith(String[] parts, String leftVar, Map<String, VariableState> postStates) {
        if (parts.length < 5) return;

        VariableState leftState = postStates.get(leftVar);
        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        VariableState state1 = getAbstractValue(operand1, postStates);
        VariableState state2 = getAbstractValue(operand2, postStates);

        if (state1.isBottom() || state2.isBottom()){
            leftState.markAsBottom();
            return;
        }

        if (state1.isTop() || state2.isTop() || state1.pointsTo != null || state2.pointsTo != null ) {
            leftState.markAsTop();
            return;
        }

        try {
            Integer value1 = state1.hasConstantValue() ? state1.getConstantValue() : null;
            Integer value2 = state2.hasConstantValue() ? state2.getConstantValue() : null;
            if (value1 != null && value2 != null) {
                Integer result = performArithmetic(operation, value1, value2);
                if (result != null) {
                    leftState.setConstantValue(result);
                } else {
                    leftState.markAsBottom();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleCmp(String[] parts, String leftVar, Map<String, VariableState> postStates) {
        if (parts.length < 5) return;

        VariableState leftState = postStates.get(leftVar);
        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        VariableState state1 = getAbstractValue(operand1, postStates);
        VariableState state2 = getAbstractValue(operand2, postStates);

        if (state1.isBottom() || state2.isBottom()){
            leftState.markAsBottom();
            return;
        }

        if (state1.isTop() || state2.isTop() || state1.pointsTo != null || state2.pointsTo != null ) {
            leftState.markAsTop();
            return;
        }

        try {
            Integer value1 = state1.hasConstantValue() ? state1.getConstantValue() : null;
            Integer value2 = state2.hasConstantValue() ? state2.getConstantValue() : null;
            if (value1 != null && value2 != null) {
                boolean result = performComparison(operation, value1, value2);
                leftState.markAsTop();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseLirFile(String filePath) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            boolean isMainFunction = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if(line.length() == 0) continue;
                if (line.startsWith("fn main")) {
                    isMainFunction = true;
                } else if (isMainFunction && line.startsWith("}")) {
                    isMainFunction = false;
                    currentBlock = null;
                } else if (isMainFunction) {
                    if (line.matches("^\\w+:")) {
                        currentBlock = line.replace(":", "");
                        blockVars.putIfAbsent(currentBlock, new TreeMap<>());
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
                            if (type.startsWith("&int")) {
                                newState.setPointsTo(type.substring(1));
                            }else if (type.equals("int")) {
                                newState.setInt(true);
                            }else if (type.startsWith("&")) {
                                newState.setPointsTo(type.substring(1));
                            }
                            globalVariables.add(varName);
                            variableStates.put(varName, newState);
                        }
                    } else if (line.contains("$addrof")) {
                        Operation newOp = new Operation(currentBlock, line);
                        basicBlocks.get(currentBlock).add(newOp);
                        String[] parts = line.split(" ");
                        TreeMap<String, String> varsInBlock = blockVars.get(currentBlock);
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (variableStates.containsKey(part) && variableStates.get(part).isInt()) {
                                varsInBlock.put(part, "");
                            }
                        }
                        if (parts.length > 3) {
                            String address = parts[0];
                            String pointTo = parts[3];
                            variableStates.get(address).setPointsTo(pointTo);
                            String addressTakenVar = parts[3];
                            addressTakenVariables.add(addressTakenVar);
                        }
                    } else {
                        TreeMap<String, String> varsInBlock = blockVars.get(currentBlock);
                        String[] parts = line.split(" ");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (variableStates.containsKey(part) && variableStates.get(part).isInt()) {
                                varsInBlock.put(part, "");
                            }
                        }
                        if (currentBlock != null) {
                            Operation newOp = new Operation(currentBlock, line);
                            basicBlocks.get(currentBlock).add(newOp);
                            if (line.startsWith("$jump")) {
                                String targetBlock = extractTargetBlock(line);
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(targetBlock);
                            }else if (line.startsWith("$branch")) {
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[2]); // trueBlock
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[3]); // falseBlock
                            }else if (line.contains("then")){
                                String targetBlock = line.substring(line.lastIndexOf("then") + 5).trim();
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(targetBlock);
                            }
                        }else{
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

    private static void printAnalysisResults(Set<String> processedBlocks, TreeMap<String, TreeMap<String, VariableState>> preStates) {
        // Sort the basic block names alphabetically
        List<String> sortedProcessedBlocks = new ArrayList<>(processedBlocks);
        Collections.sort(sortedProcessedBlocks);

        for (String blockName : sortedProcessedBlocks) {
            TreeMap<String, VariableState> varStates = preStates.get(blockName);
            System.out.println(blockName + ":");

            if (varStates == null || varStates.isEmpty()) {
                System.out.println();
                continue;
            }
            for (Map.Entry<String, VariableState> varEntry : varStates.entrySet()) {
                String varName = varEntry.getKey();
                VariableState varState = varEntry.getValue();

                // Print the variable name and its state
                if (varState.isInt()) {
                    if (varState.isTop()) {
                        System.out.println(varName + " -> Top");
                    } else if (varState.hasConstantValue()) {
                        System.out.println(varName + " -> " + varState.getConstantValue());
                    }
                }
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

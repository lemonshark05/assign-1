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
        Map<String, TreeMap<String, String>> blockVars = new HashMap<>();
        Set<String> processedBlocks = new HashSet<>();
        Queue<String> worklist = new LinkedList<>();
        Map<String, VariableState> preStates = new HashMap<>();
        Map<String, VariableState> postStates = new HashMap<>();

        parseLirFile(filePath, basicBlocks, variableStates, blockSuccessors, blockVars);
        for (String blockName : basicBlocks.keySet()) {
            if (!preStates.containsKey(blockName)) {
                preStates.put(blockName, new VariableState());
            }
        }

        worklist.add("entry");
        processedBlocks.add("entry");
        preStates.put("entry", new VariableState());

        while (!worklist.isEmpty()) {
            String block = worklist.poll();
            VariableState preState = preStates.get(block);
            VariableState postState = analyzeBlock(processedBlocks, worklist, basicBlocks.get(block), preState, variableStates, blockVars);
            postStates.put(block, postState);

            for (String successor : blockSuccessors.getOrDefault(block, new ArrayList<>())) {
                VariableState successorPreState = preStates.getOrDefault(successor, new VariableState());
                VariableState updatedPreState = successorPreState.join(postState);
                if (!updatedPreState.equals(successorPreState)) {
                    preStates.put(successor, updatedPreState);
                    worklist.add(successor);
                    processedBlocks.add(successor);
                }
            }
        }
        printAnalysisResults(processedBlocks, variableStates, blockVars);
    }

    private static VariableState analyzeBlock(Set<String> processedBlocks, Queue<String> worklist, List<Operation> operations, VariableState preState, Map<String, VariableState> variableStates, Map<String, TreeMap<String, String>> blockVars) {
        VariableState currentState = preState.clone();
        for (Operation operation : operations) {
            currentState = analyzeInstruction(processedBlocks ,worklist, operation, currentState, variableStates, blockVars);
        }
        return currentState;
    }

    private static VariableState analyzeInstruction(Set<String> processedBlocks, Queue<String> worklist, Operation operation, VariableState currentState, Map<String, VariableState> variableStates, Map<String, TreeMap<String, String>> blockVars) {
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
                    break;
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
                    if(variableStates.get(leftVar)!=null) {
                        variableStates.get(leftVar).markAsTop();
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
                case "jump":
                    String targetBlockJump = extractTargetBlock(instruction);
                    if (!worklist.contains(targetBlockJump)) {
                        worklist.add(targetBlockJump);
                        processedBlocks.add(targetBlockJump);
                        updateBlockVars(operation.block, variableStates, blockVars);
                        propagateVars(targetBlockJump, blockVars, operation.block);
                    }
                    break;
                case "branch":
                    String condition = parts[1];
                    String trueTarget = parts[2];
                    String falseTarget = parts[3];
                    updateBlockVars(operation.block, variableStates, blockVars);
                    try {
                        int conValue = Integer.parseInt(condition);
                        if (conValue != 0) {
                            worklist.add(trueTarget);
                            processedBlocks.add(trueTarget);
                            propagateVars(trueTarget, blockVars, operation.block);
                        } else{
                            worklist.add(falseTarget);
                            processedBlocks.add(falseTarget);
                            propagateVars(falseTarget, blockVars, operation.block);
                        }
                    } catch (NumberFormatException e) {
                        VariableState conditionVar = variableStates.get(condition);
                        if(conditionVar != null) {
                            if (conditionVar.hasConstantValue() && conditionVar.getConstantValue() != 0) {
                                worklist.add(trueTarget);
                                processedBlocks.add(trueTarget);
                                propagateVars(trueTarget, blockVars, operation.block);
                            } else if (conditionVar.hasConstantValue() && conditionVar.getConstantValue() == 0) {
                                worklist.add(falseTarget);
                                processedBlocks.add(falseTarget);
                                propagateVars(falseTarget, blockVars, operation.block);
                            } else {
                                worklist.add(trueTarget);
                                processedBlocks.add(trueTarget);
                                propagateVars(trueTarget, blockVars, operation.block);
                                worklist.add(falseTarget);
                                processedBlocks.add(falseTarget);
                                propagateVars(falseTarget, blockVars, operation.block);
                            }
                        }
                    }
                    break;
                case "ret":
                    updateBlockVars(operation.block, variableStates, blockVars);
                    break;
                default:
                    break;
            }
        }
        return currentState;
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

    private static VariableState getAbstractValue(String operand, Map<String, VariableState> variableStates) {
        VariableState state =new VariableState();
        try {
            int value = Integer.parseInt(operand);
            state.setConstantValue(value);
        } catch (NumberFormatException e) {
            // Not an integer, so it should be a variable name
            state = variableStates.get(operand);
        }
        return state;
    }

    private static void handleArith(String[] parts, String leftVar, Map<String, VariableState> variableStates) {
        if (parts.length < 5) return;

        VariableState leftState = variableStates.get(leftVar);
        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        VariableState state1 = getAbstractValue(operand1, variableStates);
        VariableState state2 = getAbstractValue(operand2, variableStates);

        if (state1.isBottom() || state2.isBottom()){
            leftState.markAsBottom();
            return;
        }

        if (state1.isTop() || state2.isTop()) {
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
                    leftState.markAsTop();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
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
            operand1State = variableStates.get(operand1);
            if(operand1State!=null){
                if(operand1State.isTop){
                    isTop = true;
                }else if(operand1State.hasConstantValue()){
                    value1 = operand1State.getConstantValue();
                }else if(!operand1State.isInt()){
                    //pointer ->T
                    isTop = true;
                }else{
                    isTop = false;
                }
            }
        }

        try {
            value2 = Integer.parseInt(operand2);
        } catch (NumberFormatException e) {
            operand2State = variableStates.get(operand2);
            if(operand2State!=null){
                if(operand2State.isTop){
                    isTop = true;
                }else if(operand2State.hasConstantValue()){
                    value2 = operand2State.getConstantValue();
                }else if(!operand1State.isInt()){
                    //pointer ->T
                    isTop = true;
                }else{
                    isTop = false;
                }
            }
        }

        VariableState leftState = variableStates.get(leftVar);

        if (isTop) {
            leftState.markAsTop();
        } else if (value1 != null && value2 != null) {
            result = performComparison(operation, value1, value2);
            leftState.markAsTop();
        } else if (operand1State !=null && operand1State.hasConstantValue() && operand2State !=null && operand2State.hasConstantValue()){
            leftState.markAsTop();
        }
    }

    private static void parseLirFile(String filePath, Map<String, List<Operation>> basicBlocks, Map<String, VariableState> variableStates, Map<String, List<String>> blockSuccessors, Map<String, TreeMap<String, String>> blockVars) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            boolean isMainFunction = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if(line.length() == 0) continue;
                if (line.startsWith("fn main()")) {
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
                            }else if(type.equals("int")){
                                newState.setInt(true);
                            }
                            globalVariables.add(varName);
                            variableStates.put(varName, newState);
                        }
                    } else if (line.contains("$addrof")) {
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
                            }
                            if (line.startsWith("$branch")) {
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[2]); // trueBlock
                                blockSuccessors.computeIfAbsent(currentBlock, k -> new ArrayList<>()).add(parts[3]); // falseBlock
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

    private static void printAnalysisResults(Set<String> processedBlocks, Map<String, VariableState> variableStates, Map<String, TreeMap<String, String>> blockVars) {
        // Sort the basic block names alphabetically
        List<String> sortedProcessedBlocks = new ArrayList<>(processedBlocks);
        Collections.sort(sortedProcessedBlocks);

        for (String blockName : sortedProcessedBlocks) {
            System.out.println(blockName + ":");
            TreeMap<String, String> varsInBlock = blockVars.getOrDefault(blockName, new TreeMap<>());
            for (Map.Entry<String, String> entry : varsInBlock.entrySet()) {
                String varName = entry.getKey();
                String varValue = entry.getValue();
                if(!varValue.equals("null")) {
                    System.out.println(varName + " -> " + varValue);
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
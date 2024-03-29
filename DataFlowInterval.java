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

    static IntervalArithmetic arithmetic = new IntervalArithmetic();
    static Set<String> addressTakenVariables = new HashSet<>();
    static Map<String, VarInterval> addressTakenVarInit = new TreeMap<>();
    static Set<String> allVars = new HashSet<>();

    static Set<String> globalIntVars = new HashSet<>();
    static Set<String> localIntParams = new HashSet<>();

    static Map<String, List<Operation>> basicBlocks = new HashMap<>();
    static Map<String, List<String>> blockSuccessors = new HashMap<>();

    static Map<String, TreeMap<String, String>> blockVars = new HashMap<>();
    static Map<String, VarInterval> varIntervals = new TreeMap<>();
    static Set<String> processedBlocks = new HashSet<>();

    static Queue<String> worklist = new PriorityQueue<>();

    static TreeMap<String, TreeMap<String, VarInterval>> preStates = new TreeMap<>();
    static TreeMap<String, TreeMap<String, VarInterval>> postStates = new TreeMap<>();

    static Set<String> visited = new HashSet<>();
    static Set<String> loopHeaders = new HashSet<>();
    static LinkedList<String> stack = new LinkedList<>();

    private static void dfs(String block) {
        if (visited.contains(block)) {
            return;
        }
        visited.add(block);
        stack.push(block);

        for (String successor : blockSuccessors.getOrDefault(block, new ArrayList<>())) {
            if (stack.contains(successor)) {
                loopHeaders.add(successor);
            } else {
                dfs(successor);
            }
        }
        stack.pop();
    }

    private static void getLoopHeaders() {
        dfs("entry");
    }

    public static void dataFlow(String filePath, String functionName) {
        parseLirFile(filePath, functionName);
        getLoopHeaders();
        for(String varName: addressTakenVariables){
            VarInterval thisVar = varIntervals.get(varName);
            if (thisVar.isInt()) {
                VarInterval newState = new VarInterval();
                newState.setInt(true);
                newState.markAsTop();
                addressTakenVarInit.put(varName, newState);
            }
        }
        for (String blockName : blockVars.keySet()) {
            TreeMap<String, VarInterval> initialStates = new TreeMap<>();
            TreeMap<String, String> varsInBlock = blockVars.get(blockName);
            for (Map.Entry<String, String> entry : varsInBlock.entrySet()) {
                String varName = entry.getKey();
                VarInterval newState = varIntervals.get(varName).clone();
                newState.markAsBottom();
                initialStates.put(varName, newState);
            }

            if(blockName.equals("entry")){
                for (String addTVar : addressTakenVarInit.keySet()) {
                    VarInterval initState = new VarInterval();
                    initState.markAsBottom();
                    initState.setInt(true);
                    initialStates.put(addTVar, initState);
                }
            }

            for(String globalVar : globalIntVars){
                VarInterval newState = new VarInterval();
                newState.setInt(true);
                newState.markAsTop();
                initialStates.put(globalVar, newState);
            }
            preStates.put(blockName, initialStates);
        }

        TreeMap<String, VarInterval> entryStates = preStates.get("entry");
        for (String param : localIntParams) {
            VarInterval newState = varIntervals.get(param).clone();
            entryStates.put(param, newState);
        }

        worklist.add("entry");
        processedBlocks.add("entry");

        while (!worklist.isEmpty()) {
            String block = worklist.poll();
            if(block.equals("bb5")){
                String a = "";
            }
//            System.out.println("Poll Worklist: " + worklist.toString());
            TreeMap<String, VarInterval> preState = preStates.get(block);
            TreeMap<String, VarInterval> postState = analyzeBlock(block, preState, processedBlocks);
            postStates.put(block, postState);
//            printVariableIntervals(block, "After Analysis", postState);

            for (String successor : blockSuccessors.getOrDefault(block, new LinkedList<>())) {
                if(successor.equals("bb4")){
                    String a = "";
                }
                TreeMap<String, VarInterval> successorPreState = preStates.get(successor);
//                printVariableIntervals(successor, "successorPreState", successorPreState);

                TreeMap<String, VarInterval> newState = new TreeMap<>(successorPreState);
                for (Map.Entry<String, VarInterval> entry : successorPreState.entrySet()) {
                    String varName = entry.getKey();
                    VarInterval originalVarState = entry.getValue().clone();
                    newState.put(varName, originalVarState);
                }

                if (loopHeaders.contains(successor)) {
                    for (Map.Entry<String, VarInterval> entry : successorPreState.entrySet()) {
                        String varName = entry.getKey();
                        VarInterval originalVarState = entry.getValue();
                        Interval widenedInterval = originalVarState.getInterval();
                        if (postState.containsKey(varName)) {
                            Interval prevStateInterval = postState.get(varName).getInterval();
                            widenedInterval = originalVarState.getInterval().widen(prevStateInterval);
                            VarInterval widenedVarState = originalVarState.clone();
                            widenedVarState.setInterval(widenedInterval);
                            successorPreState.put(varName, widenedVarState);
                            newState.put(varName, widenedVarState);
                        }

                    }
//                    printVariableIntervals(successor, "After loopHeaders Widening", successorPreState);
                }
                TreeMap<String, VarInterval> joinedState = joinMaps(newState, postState);
                preStates.put(successor, joinedState);
//                printVariableIntervals(successor, "After Joins PreStates", joinedState);
                if (!joinedState.equals(successorPreState) || postState.isEmpty()) {
                    if (!worklist.contains(successor)) {
                        processedBlocks.add(successor);
                        worklist.add(successor);
//                        System.out.println("Add to Worklist: " + worklist.toString());
                    }
                }
                if (!processedBlocks.contains(successor)) {
                    processedBlocks.add(successor);
                    worklist.add(successor);
                }
            }
        }
        printAnalysisResults(processedBlocks, postStates);
    }

    private static void printVariableIntervals(String block, String stage, TreeMap<String, VarInterval> state) {
        VarInterval iVarState = state.get("i");
        VarInterval t9VarState = state.get("_t9");

        System.out.println("Block: " + block + ", Stage: " + stage);
        if (iVarState != null) {
            System.out.println("i interval: " + iVarState.getInterval());
        } else {
            System.out.println("i interval: Buttom");
        }
        if (t9VarState != null) {
            System.out.println("_t9 interval: " + t9VarState.getInterval());
        } else {
            System.out.println("_t9 interval: Buttom");
        }
        System.out.println();
    }

    private static TreeMap<String, VarInterval> analyzeBlock(String block, TreeMap<String, VarInterval> pState, Set<String> processedBlocks) {
        TreeMap<String, VarInterval> postState = new TreeMap<>();
        for (Map.Entry<String, VarInterval> entry : pState.entrySet()) {
            VarInterval newState = entry.getValue().clone();
            postState.put(entry.getKey(), newState);
        }
        for (Operation operation : basicBlocks.get(block)) {
            analyzeInstruction(postState, processedBlocks ,operation);
        }
        return postState;
    }

    private static TreeMap<String, VarInterval> joinMaps(TreeMap<String, VarInterval> map1, TreeMap<String, VarInterval> map2) {
        TreeMap<String, VarInterval> result = new TreeMap<>(map1);

        for (Map.Entry<String, VarInterval> entry : map1.entrySet()) {
            VarInterval newState = entry.getValue().clone();
            result.put(entry.getKey(), newState);
        }

        for (Map.Entry<String, VarInterval> entry : map2.entrySet()) {
            String varName = entry.getKey();
            VarInterval stateFromMap2 = entry.getValue();
            if (result.containsKey(varName)) {
                VarInterval stateFromMap1 = result.get(varName);
                VarInterval mergedState = stateFromMap1.join(stateFromMap2);
                result.put(varName, mergedState);
//                System.out.println("Merging state for variable '" + varName + "': " + stateFromMap1 + " ⊔ " + stateFromMap2 + " = " + mergedState);
            } else {
                result.put(varName, stateFromMap2);
//                System.out.println("Adding new state for variable '" + varName + "': " + stateFromMap2);
            }
        }

        return result;
    }

    private static void analyzeInstruction(TreeMap<String, VarInterval> postState, Set<String> processedBlocks, Operation operation) {
        String instruction = operation.instruction;
        Pattern operationPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp|ret|call_dir|call_idr|jump|branch)");
        Matcher matcher = operationPattern.matcher(instruction);
        String[] parts = instruction.split(" ");
        String leftVar = parts[0];
        if(leftVar.equals("_t9")){
            String a = "";
        }
        if (matcher.find()) {
            String opera = matcher.group(1);
            switch (opera) {
                case "store":
                    String pointerVar = parts[1];
                    String valueVarOrConstant = parts[2];
                    if (valueVarOrConstant.matches("\\d+")) {
                        int contant = Integer.parseInt(valueVarOrConstant);
                        for(String addVar : addressTakenVarInit.keySet()){
                            VarInterval newState = new VarInterval();
                            newState.setInt(true);
                            newState.setConstantValue(contant);
                            VarInterval mergeVar = postState.get(addVar).join(newState);
                            postState.put(addVar, mergeVar);
                        }
                    } else if (postState.containsKey(valueVarOrConstant)) {
                        VarInterval valueState = postState.get(valueVarOrConstant);
                        for(String addVar : addressTakenVarInit.keySet()){
                            VarInterval mergeVar = postState.get(addVar).join(valueState);
                            postState.put(addVar, mergeVar);
                        }
                    }
                    break;
                case "load":
                    if (postState.containsKey(leftVar)) {
                        VarInterval loadedState = postState.get(leftVar);
                        loadedState.markAsTop();
                        loadedState.setInterval(new Interval(null, null));
                    }
                    break;
                case "alloc":
                    if (postState.containsKey(leftVar)) {
                        VarInterval loadedState = postState.get(leftVar);
                        loadedState.markAsTop();
                        loadedState.setInterval(new Interval(null, null));
                    }
                    break;
                case "cmp":
                    handleCmp(parts, leftVar, postState);
                    break;
                case "arith":
                    handleArith(parts, leftVar, postState);
                    break;
                case "gep":
                    if (postState.containsKey(leftVar)) {
                        postState.get(leftVar).markAsTop();
                        postState.get(leftVar).setInterval(new Interval(null, null));
                    }
                    break;
                case "copy":
                    if(leftVar.equals("id1")){
                        String a = "";
                    }
                    if (parts.length > 3) {
                        String copiedVar = parts[3];
                        VarInterval updateVar = postState.get(leftVar);
                        VarInterval copiedState = postState.get(copiedVar);
                        if(updateVar == null){
                            updateVar = varIntervals.get(leftVar);
                        }
                        if(copiedState == null){
                            copiedState = varIntervals.get(copiedVar);
                        }
                        if (copiedState != null) {
                            if (copiedState.getPointsTo() != null) {
                                updateVar.setPointsTo(copiedState.getPointsTo());
                            } else if (copiedState.isInt() && copiedState.hasConstantValue()) {
                                updateVar.setConstantValue(copiedState.getConstantValue());
                            } else if(copiedState.isBottom()) {
                                updateVar.markAsBottom();
                            }else {
                                updateVar.markAsTop();
//                                Interval merger = updateVar.interval.join(updateVar.interval, copiedState.interval);
                            }
                            updateVar.setInterval(new Interval(copiedState.interval.getMin(), copiedState.interval.getMax()));
                        } else {
                            try {
                                int value = Integer.parseInt(copiedVar);
                                updateVar.setConstantValue(value);
                            } catch (NumberFormatException e) {
                                if(updateVar!=null) {
                                    updateVar.markAsTop();
                                    updateVar.setInterval(new Interval(null, null));
                                }
                            }
                        }
                    }
                    break;
                case "call_ext":
                    if(postState.get(leftVar)!=null) {
                        postState.get(leftVar).markAsTop();
                        postState.get(leftVar).setInterval(new Interval(null, null));
                    }
                    if (instruction.contains("(") && instruction.contains(")")) {
                        String argumentsSubstring = instruction.substring(instruction.indexOf('(') + 1, instruction.indexOf(')'));
                        String[] argumentVars = argumentsSubstring.split(",");

                        for (String var : argumentVars) {
                            String varName = var.trim();
                            if(varIntervals.containsKey(varName)) {
                                String pointedVar = varIntervals.get(varName).getPointsTo();
                                if (pointedVar !=null) {
                                    for(String updateVar : addressTakenVarInit.keySet()){
                                        postState.get(updateVar).markAsTop();
                                        postState.get(updateVar).setInterval(new Interval(null, null));
                                    }
                                }
                            }
                        }
                    }
                    if (instruction.contains("then")) {
                        String targetBlock = instruction.substring(instruction.lastIndexOf("then") + 5).trim();
                        worklist.add(targetBlock);
                        processedBlocks.add(targetBlock);
                    }
                    break;
                case "call_dir":
                    if(postState.get(leftVar)!=null) {
                        postState.get(leftVar).markAsTop();
                        postState.get(leftVar).setInterval(new Interval(null, null));
                    }
                    if (instruction.contains("(") && instruction.contains(")")) {
                        String argumentsSubstring = instruction.substring(instruction.indexOf('(') + 1, instruction.indexOf(')'));
                        String[] argumentVars = argumentsSubstring.split(",");

                        for (String var : argumentVars) {
                            String varName = var.trim();
                            if(varIntervals.containsKey(varName)) {
                                String pointedVar = varIntervals.get(varName).getPointsTo();
                                if (pointedVar !=null) {
                                    for(String updateVar : addressTakenVarInit.keySet()){
                                        postState.get(updateVar).markAsTop();
                                        postState.get(updateVar).setInterval(new Interval(null, null));
                                    }
                                }
                            }
                        }
                    }
                    if (instruction.contains("then")) {
                        String targetBlock = instruction.substring(instruction.lastIndexOf("then") + 5).trim();
                        worklist.add(targetBlock);
                        processedBlocks.add(targetBlock);
                    }
                    break;
                case "call_idr":
                    if(postState.get(leftVar)!=null) {
                        postState.get(leftVar).markAsTop();
                        postState.get(leftVar).setInterval(new Interval(null, null));
                    }
                    if (instruction.contains("(") && instruction.contains(")")) {
                        String argumentsSubstring = instruction.substring(instruction.indexOf('(') + 1, instruction.indexOf(')'));
                        String[] argumentVars = argumentsSubstring.split(",");

                        for (String var : argumentVars) {
                            String varName = var.trim();
                            if(varIntervals.containsKey(varName)) {
                                String pointedVar = varIntervals.get(varName).getPointsTo();
                                if (pointedVar !=null) {
                                    for(String updateVar : addressTakenVarInit.keySet()){
                                        postState.get(updateVar).markAsTop();
                                        postState.get(updateVar).setInterval(new Interval(null, null));
                                    }
                                }
                            }
                        }
                    }
                    if (instruction.contains("then")) {
                        String targetBlock = instruction.substring(instruction.lastIndexOf("then") + 5).trim();
                        worklist.add(targetBlock);
                        processedBlocks.add(targetBlock);
                    }
                    break;
                case "addrof":
                    if (parts.length > 2) {
                        String pointedVar = parts[3];
                        varIntervals.get(leftVar).setPointsTo(pointedVar);
                    }
                    break;
                case "gfp":
                    if (postState.containsKey(leftVar)) {
                        postState.get(leftVar).markAsTop();
                        postState.get(leftVar).setInterval(new Interval(null, null));
                    }
                    break;
                case "jump":
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
                        } else{
                            newSuccessor.add(falseTarget);
                            blockSuccessors.put(operation.block, newSuccessor);
                        }
                    } catch (NumberFormatException e) {
                        VarInterval conditionVar = postState.get(condition);
                        if(conditionVar != null) {
                            if (conditionVar.isBottom()) {
                                blockSuccessors.put(operation.block, newSuccessor);
                                break;
                            } else if(conditionVar.hasConstantValue() && conditionVar.getConstantValue() != 0) {
                                newSuccessor.add(trueTarget);
                                blockSuccessors.put(operation.block, newSuccessor);
                            } else if (conditionVar.hasConstantValue() && conditionVar.getConstantValue() == 0) {
                                newSuccessor.add(falseTarget);
                                blockSuccessors.put(operation.block, newSuccessor);
                            } else {
                                newSuccessor.add(trueTarget);
                                newSuccessor.add(falseTarget);
                                blockSuccessors.put(operation.block, newSuccessor);
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

    private static String getAbstractValue(String operand, Map<String, VarInterval> postStates) {
        VarInterval state =new VarInterval();
        try {
            int value = Integer.parseInt(operand);
            return value+"";
        } catch (NumberFormatException e) {
            // Not an integer, so it should be a variable name
            if(postStates.containsKey(operand)) {
                state = postStates.get(operand);
            }else{
                state = varIntervals.get(operand);
            }
            if(state.isTop){
                return "T";
            }else if(state.hasConstantValue()){
                int stateValue = state.getConstantValue();
                return stateValue+"";
            }else if(state.isBottom()){
                return "B";
            }else if(state.pointsTo != null){
                return "T";
            }
        }
        return "";
    }

    private static Interval getInterval(String operand, Map<String, VarInterval> postStates) {
        Interval state =new Interval();
        try {
            int value = Integer.parseInt(operand);
            state.setInterval(value, value);
            return state;
        } catch (NumberFormatException e) {
            // Not an integer, so it should be a variable name
            VarInterval newState = new VarInterval();
            if(postStates.containsKey(operand)) {
                newState = postStates.get(operand);
            }else{
                newState = varIntervals.get(operand);
            }
            state = newState.getInterval();
        }
        return state;
    }

    private static void handleArith(String[] parts, String leftVar, Map<String, VarInterval> postState) {
        if (parts.length < 5) return;

        if(leftVar.equals("_t5")){
            String a = leftVar;
        }

        VarInterval leftState = postState.get(leftVar);
        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        String state1 = getAbstractValue(operand1, postState);
        String state2 = getAbstractValue(operand2, postState);

        Interval interval1 = getInterval(operand1, postState);
        Interval interval2 = getInterval(operand2, postState);
        Interval resultInterval = arithmetic.performArithInterval(operation, interval1, interval2);
        leftState.setInterval(new Interval(resultInterval.getMin(), resultInterval.getMax()));

        if (state1.equals("B") || state2.equals("B")){
            leftState.markAsBottom();
            return;
        }

        if(operation.equals("mul")){
            if(state1.equals("0") || state2.equals("0")){
                leftState.setConstantValue(0);
                leftState.setInterval(new Interval(0, 0));
                return;
            }
        }

        if(operation.equals("div")){
            if(state2.equals("0")){
                leftState.markAsBottom();
                return;
            }else if(state1.equals("0")){
                leftState.setConstantValue(0);
                leftState.setInterval(new Interval(0, 0));
                return;
            }
        }

        if (state1.equals("T") || state2.equals("T")) {
            leftState.markAsTop();
            return;
        }

        try {
            Integer value1 = Integer.parseInt(state1);
            Integer value2 = Integer.parseInt(state2);
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

    private static void handleCmp(String[] parts, String leftVar, Map<String, VarInterval> postStates) {
        if (parts.length < 5) return;

        VarInterval leftState = postStates.get(leftVar);
        String operation = parts[3];
        String operand1 = parts[4];
        String operand2 = parts[5];

        String state1 = getAbstractValue(operand1, postStates);
        String state2 = getAbstractValue(operand2, postStates);

        if (state1.equals(state2) && state1.equals("B")) {
            leftState.markAsBottom();
            return;
        }

        leftState.setInterval(new Interval(0, 1));

        if(state1.equals(state2) && state1.equals("T")){
            leftState.markAsTop();
            return;
        }else if(state1.length() == 0){
            leftState.markAsBottom();
            return;
        }else if (state1.equals("B") || state2.equals("B")){
            leftState.markAsBottom();
            return;
        }else if (state1.equals("T") || state2.equals("T")) {
            leftState.markAsTop();
            return;
        }

        try {
            Integer value1 = Integer.parseInt(state1);
            Integer value2 = Integer.parseInt(state2);
            if (value1 != null && value2 != null) {
                boolean result = performComparison(operation, value1, value2);
                if(!result){
                    leftState.setConstantValue(0);
                }else{
                    leftState.setConstantValue(1);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void parseLirFile(String filePath, String functionName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String currentBlock = null;
            boolean isFunction = false;
            boolean isStruct = false;

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if(line.length() == 0) continue;
                if (line.startsWith("fn "+functionName)) {
                    isFunction = true;
                    if(line.contains(":") && line.contains("(")) {
                        String paramSubstring = line.substring(line.indexOf('(') + 1, line.indexOf(')'));
                        StringBuilder transformedPart = new StringBuilder();
                        int parenthesisLevel = 0;
                        for (char c : paramSubstring.toCharArray()) {
                            if (c == '(') {
                                parenthesisLevel++;
                            }else if (c == ')'){
                                parenthesisLevel--;
                            } else if (c == ',' && parenthesisLevel > 0){
                                c = '|';
                            }
                            transformedPart.append(c);
                        }
                        String[] variables = paramSubstring.toString().split(",\\s*");
                        for (String varDeclaration : variables) {
                            String[] parts = varDeclaration.split(":");
                            String varName = parts[0].trim();
                            // just get int type
                            String type = parts[1].trim();
                            VarInterval newState = new VarInterval();
                            if (type.startsWith("&int")) {
                                newState.setPointsTo(type.substring(1));
                            } else if (type.equals("int")) {
                                newState.setInt(true);
                            } else if (type.startsWith("&")) {
                                newState.setPointsTo(type.substring(1));
                            }
                            newState.markAsTop();
                            localIntParams.add(varName);
                            allVars.add(varName);
                            varIntervals.put(varName, newState);
                        }
                    }
                } else if (isFunction && line.startsWith("}")) {
                    isFunction = false;
                    currentBlock = null;
                } else if(line.startsWith("struct ")){
                    isStruct = true;
                } else if(isStruct && line.startsWith("}")) {
                    isStruct = false;
                } else if (!isFunction && !isStruct && line.matches("^\\w+:int$")) {
                    Pattern pattern = Pattern.compile("^(\\w+):int$");
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        String varName = matcher.group(1);
                        globalIntVars.add(varName);
                        allVars.add(varName);
                    }
                } else if (isFunction) {
                    if (line.matches("^\\w+:")) {
                        currentBlock = line.replace(":", "");
                        blockVars.putIfAbsent(currentBlock, new TreeMap<>());
                        basicBlocks.putIfAbsent(currentBlock, new ArrayList<>());
                    } else if (line.startsWith("let ")) {
                        String variablesPart = line.substring("let ".length());
                        StringBuilder transformedPart = new StringBuilder();
                        int parenthesisLevel = 0;
                        for (char c : variablesPart.toCharArray()) {
                            if (c == '(') {
                                parenthesisLevel++;
                            }else if (c == ')'){
                                parenthesisLevel--;
                            } else if (c == ',' && parenthesisLevel > 0){
                                c = '|';
                            }
                            transformedPart.append(c);
                        }
                        String[] variables = transformedPart.toString().split(",\\s*");
                        for (String varDeclaration : variables) {
                            String[] parts = varDeclaration.split(":");
                            String varName = parts[0].trim();
                            // just get int type
                            String type = parts[1].trim();
                            VarInterval newState = new VarInterval();
                            if (type.startsWith("&int")) {
                                newState.setPointsTo(type.substring(1));
                            }else if (type.equals("int")) {
                                newState.setInt(true);
                            }else if (type.startsWith("&")) {
                                newState.setPointsTo(type.substring(1));
                            }
                            allVars.add(varName);
                            varIntervals.put(varName, newState);
                        }
                    } else if (line.contains("$addrof")) {
                        Operation newOp = new Operation(currentBlock, line);
                        basicBlocks.get(currentBlock).add(newOp);
                        String[] parts = line.split(" ");
                        TreeMap<String, String> varsInBlock = blockVars.get(currentBlock);
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (varIntervals.containsKey(part) && varIntervals.get(part).isInt()) {
                                varsInBlock.put(part, "");
                            }
                        }
                        if (parts.length > 3) {
                            String address = parts[0];
                            String addressTakenVar = parts[3];
                            varIntervals.get(address).setPointsTo(addressTakenVar);
                            if(varIntervals.containsKey(addressTakenVar)) {
                                addressTakenVariables.add(addressTakenVar);
                            }
                        }
                    } else {
                        TreeMap<String, String> varsInBlock = blockVars.get(currentBlock);
                        String[] parts = line.split(" ");
                        for (int i = 0; i < parts.length; i++) {
                            String part = parts[i];
                            if (varIntervals.containsKey(part)) {
                                VarInterval thisVar = varIntervals.get(part);
                                if(thisVar.isInt()) {
                                    varsInBlock.put(part, "");
                                }
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

    private static void printAnalysisResults(Set<String> processedBlocks, TreeMap<String, TreeMap<String, VarInterval>> preStates) {
        // Sort the basic block names alphabetically
        List<String> sortedProcessedBlocks = new ArrayList<>(processedBlocks);
        Collections.sort(sortedProcessedBlocks);

        for (String blockName : sortedProcessedBlocks) {
            TreeMap<String, VarInterval> varStates = preStates.get(blockName);
            System.out.println(blockName + ":");

            if (varStates == null || varStates.isEmpty()) {
                System.out.println();
                continue;
            }
            for (Map.Entry<String, VarInterval> varEntry : varStates.entrySet()) {
                String varName = varEntry.getKey();
                VarInterval varState = varEntry.getValue();

                // Print the variable name and its state
                if (varState.isInt() && !varState.isBottom()) {
                    Interval interval = varState.interval;
                    if (!varState.isBottom()) {
                        String left = "(NegInf";
                        String right = "PosInf)";
                        if(interval.getMin()!=null){
                            left = "["+ interval.getMin();
                        }
                        if(interval.getMax()!=null){
                            right = interval.getMax() + "]";
                        }
                        System.out.println(varName + " -> " + left +", "+ right);
                    }
                }
            }
            System.out.println();
        }
    }

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java DataFlowConstants <lir_file_path> <json_file_path> <function_name>");
            System.exit(1);
        }
        String lirFilePath = args[0];
        String functionName = "test";
        if(args.length > 2 && args[2].length()!=0){
            functionName = args[2];
        }
        dataFlow(lirFilePath, functionName);
    }
}

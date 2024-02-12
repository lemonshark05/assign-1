import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Pattern;

public class DataFlowInterval {
    public static void dataFlow(String filePath) {
        Map<String, VariableState> variableStates = new HashMap<>();
        Queue<String> worklist = new LinkedList<>();
        Map<String, String[]> dependencies = new HashMap<>();
        Pattern instructionPattern = Pattern.compile("\\$(store|load|alloc|cmp|gep|copy|call_ext|addrof|arith|gfp)");
        Pattern terminalPattern = Pattern.compile("\\$(branch|jump|ret|call_dir|call_idr)");

        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();

                if (line.startsWith("entry")) {
                    System.out.println("entry:");
                } else if (line.startsWith("let ")) {
                    String vars = line.substring(4);
                    for (String var : vars.split(",")) {
                        String[] parts = var.trim().split(":");
                        String varName = parts[0].trim();
                        String type = parts[1].trim();
                        VariableState newState = new VariableState();
                        if (type.startsWith("&")) {
                            newState.setPointsTo(type.substring(1));
                        }
                        variableStates.put(varName, newState);
                    }
                } else if(line.contains("=")){
                    String leftVar = line.substring(0, line.indexOf('=')).trim();
                    String rightExpr = line.substring(line.indexOf('=') + 1).trim();

                    if (rightExpr.contains("$addrof")) {
                        String pointedVar = rightExpr.split(" ")[1];
                        variableStates.get(leftVar).setPointsTo(pointedVar);
                    } if (rightExpr.contains("$copy")) {
                        String copiedVar = rightExpr.split(" ")[1];
                        VariableState copiedState = variableStates.get(copiedVar);
                        if (copiedState != null && copiedState.pointsTo != null) {
                            variableStates.get(leftVar).setPointsTo(copiedState.pointsTo);
                        }
                    } else if (rightExpr.contains("$load") || rightExpr.contains("$arith")) {
                        dependencies.put(leftVar, new String[]{rightExpr});
                        worklist.add(leftVar);
                    }
                } else if (line.startsWith("$store")) {
                    String[] parts = line.split(" ");
                    if (parts.length >= 3) {
                        String pointerVar = parts[1];
                        VariableState pointerState = variableStates.get(pointerVar);

                        if (pointerState != null && pointerState.pointsTo != null) {
                            String targetVar = pointerState.pointsTo;
                            VariableState targetState = variableStates.get(targetVar);
                            if (targetState == null) {
                                targetState = new VariableState();
                                variableStates.put(targetVar, targetState);
                            }
                            targetState.markAsTop();
                            dependencies.put(targetVar, new String[]{parts[2]});
                            worklist.add(targetVar);
                        }
                    }
                } else if (line.startsWith("$ret")) {
                    String retVar = line.substring(line.indexOf(' ') + 1).trim();
                    VariableState retState = variableStates.get(retVar);
                    if (retState != null && retState.pointsTo != null) {
                        retState.markAsTop();
                        System.out.println(retVar + " -> Top");
                        if (retVar.startsWith("&")) {
                            worklist.add(retVar);
                        }
                    }
                }
            }

            // Process the worklist
            while (!worklist.isEmpty()) {
                String var = worklist.poll();
                VariableState state = variableStates.get(var);
                if (state == null || state.isTop) continue;
                for (String dep : dependencies.getOrDefault(var, new String[0])) {
                    if (dep.contains("$load") || dep.contains("$arith")) {
                        state.markAsTop();
                        System.out.println(var + " -> Top");
                        break;
                    }
                }
            }

        } catch (IOException e) {
            System.err.println("Could not open file: " + filePath);
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            System.out.println("Usage: java DataFlowInterval <lir_file_path>");
            return;
        }

        String lirFilePath = args[0];
        dataFlow(lirFilePath);
    }
}
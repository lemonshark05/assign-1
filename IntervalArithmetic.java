import java.util.*;

public class IntervalArithmetic {

    public String safeDiv(String ab1 , String ab2){
        if(ab1.equals("0")){
            return "0";
        }else if(ab1.equals("T")){
            if(ab2.equals("T")){
                return "T";
            }else if(ab2.equals("B")){
                return "B";
            }else{
                Integer integer = Integer.parseInt(ab2);
                if(integer > 0){
                    return "T";
                }else{
                    return "B";
                }
            }
        }else if(ab1.equals("B")){
            if(ab2.equals("T")){
                return "B";
            }else if(ab2.equals("B")){
                return "T";
            }else{
                Integer integer = Integer.parseInt(ab2);
                if(integer > 0){
                    return "B";
                }else{
                    return "T";
                }
            }
        }else{
            Integer integer = Integer.parseInt(ab1);
            if(integer > 0){
                if(ab2.equals("T")){
                    return "0";
                }else if(ab2.equals("B")){
                    return "0";
                }else{
                    return integer / Integer.parseInt(ab2) + "";
                }
            }else{
                if(ab2.equals("T")){
                    return "B";
                }else if(ab2.equals("B")){
                    return "T";
                }else{
                    return integer * Integer.parseInt(ab2) + "";
                }
            }
        }
    }

    public String safeMult(String ab1 , String ab2){
        if(ab1.equals("0")){
            return "0";
        }else if(ab1.equals("T")){
            if(ab2.equals("0")){
                return "0";
            }else if(ab2.equals("T")){
                return "T";
            }else if(ab2.equals("B")){
                return "B";
            }else{
                Integer integer = Integer.parseInt(ab2);
                if(integer > 0){
                    return "T";
                }else{
                    return "B";
                }
            }
        }else if(ab1.equals("B")){
            if(ab2.equals("0")){
                return "0";
            }else if(ab2.equals("T")){
                return "B";
            }else if(ab2.equals("B")){
                return "T";
            }else{
                Integer integer = Integer.parseInt(ab2);
                if(integer > 0){
                    return "B";
                }else{
                    return "T";
                }
            }
        }else{
            Integer integer = Integer.parseInt(ab1);
            if(integer > 0){
                if(ab2.equals("0")){
                    return "0";
                }else if(ab2.equals("T")){
                    return "T";
                }else if(ab2.equals("B")){
                    return "B";
                }else{
                    return integer * Integer.parseInt(ab2) + "";
                }
            }else{
                if(ab2.equals("0")){
                    return "0";
                }else if(ab2.equals("T")){
                    return "B";
                }else if(ab2.equals("B")){
                    return "T";
                }else{
                    return integer * Integer.parseInt(ab2) + "";
                }
            }
        }
    }
    public String findMin(String[] res){
        for(int i = 0;i< res.length;i++){
            if(res[i] == "B"){
                return "B";
            }
        }
        Integer min = null;
        for(int i = 0;i< res.length;i++){
            if(res[i] != "T"){
                Integer temp = Integer.parseInt(res[i]);
                if(min == null){
                    min = temp;
                }else if(min > temp){
                    min = temp;
                }
            }
        }
        return min.toString();
    }

    public String findMax(String[] res){
        for(int i = 0;i< res.length;i++){
            if(res[i] == "T"){
                return "T";
            }
        }
        Integer max = null;
        for(int i = 0;i< res.length;i++){
            if(res[i] != "B"){
                Integer temp = Integer.parseInt(res[i]);
                if(max == null){
                    max = temp;
                }else if(max < temp){
                    max = temp;
                }
            }
        }
        return max.toString();
    }
    public  void main(String[] args) {
        // Test cases
//        [0, ∞] * [0, ∞] = [0, ∞]
        testMultiplication(new Interval(0, null), new Interval(0, null));
//        [0, 0] by [-∞, ∞] = [0, 0]
        testMultiplication(new Interval(0, 0), new Interval(null, null));
//        [-∞, 3] by [2, 5] = [-∞, 15]
        testMultiplication(new Interval(null, 3), new Interval(2, 5));
//        [-∞, 3] by [-∞, 5] = [-∞, ∞]
        testMultiplication(new Interval(null, 3), new Interval(null, 5));
//        [-∞, -3] by [-∞, -5] = [15, ∞]
        testMultiplication(new Interval(null, -3), new Interval(null, -5));
//        [-1, 1] by [-2, 2] = [-2, 2]
        testMultiplication(new Interval(-1, 1), new Interval(-2, 2));
//        [-∞, ∞] by [1, 3] = [-∞, ∞]
        testMultiplication(new Interval(null, null), new Interval(1, 3));
//        [-5, -3] by [-∞, -1] = [3, ∞]
        testMultiplication(new Interval(-5, -3), new Interval(null, -1));
    }

    private void testMultiplication(Interval val1, Interval val2) {
        Interval result = performArithInterval("mul", val1, val2);
        System.out.println("Multiplying " + val1 + " by " + val2 + " = " + result);
    }

    Interval performArithInterval(String op, Interval val1, Interval val2) {
        Interval result = new Interval(0,0);
        String v11 = val1.getMin() == null?"B": val1.getMin().toString();
        String v12 = val1.getMax() == null?"T": val1.getMax().toString();
        String v21 = val2.getMin() == null?"B": val2.getMin().toString();
        String v22 = val2.getMax() == null?"T": val2.getMax().toString();
        switch (op) {
            case "add":
                String[] results1 = new String[]{
                        safeAdd(v11, v21),
                        safeAdd(v11, v22),
                        safeAdd(v12, v21),
                        safeAdd(v12, v22)
                };
                String minResult1 = findMin(results1);
                String maxResult1 = findMax(results1);
                Integer min1 = minResult1 == "B"? null : Integer.parseInt(minResult1);
                Integer max1 = maxResult1 == "T"? null : Integer.parseInt(maxResult1);
                return new Interval(min1, max1);
            case "sub":
                String[] results2 = new String[]{
                        safeSubtract(v11, v21),
                        safeSubtract(v11, v22),
                        safeSubtract(v12, v21),
                        safeSubtract(v12, v22)
                };
                String minResult2 = findMin(results2);
                String maxResult2 = findMax(results2);
                Integer min2 = minResult2 == "B"? null : Integer.parseInt(minResult2);
                Integer max2 = maxResult2 == "T"? null : Integer.parseInt(maxResult2);
                return new Interval(min2, max2);
            case "mul":
                String[] results = new String[]{
                        safeMult(v11, v21),
                        safeMult(v11, v22),
                        safeMult(v12, v21),
                        safeMult(v12, v22)
                };
                String minResult = findMin(results);
                String maxResult = findMax(results);
                Integer min = minResult == "B"? null : Integer.parseInt(minResult);
                Integer max = maxResult == "T"? null : Integer.parseInt(maxResult);
                return new Interval(min, max);
            case "div":
                if(v21.equals("0") && v22.equals("0")){
//                    If `I2` = `[0, 0]`: the answer is `⊥`.
                    return result;
                }
                if(v21.equals("0")){
                    v21 = "1";
                }

                if(v22.equals("0")){
                    v21 = "-1";
                }

                if((v11.equals("B") && v12.equals("T"))){
                    return new Interval(null, null);
                }

                if(isNegative(v21) && isPositive(v22)){
                    v21 = "-1";
                    v22 = "1";
                }

                String[] results3 = new String[]{
                        safeDiv(v11, v21),
                        safeDiv(v11, v22),
                        safeDiv(v12, v21),
                        safeDiv(v12, v22)
                };

                String minResult3 = findMin(results3);
                String maxResult3 = findMax(results3);
                Integer min3 = minResult3 == "B"? null : Integer.parseInt(minResult3);
                Integer max3 = maxResult3 == "T"? null : Integer.parseInt(maxResult3);
                return new Interval(min3, max3);
            default:
//                result.;
                break;
        }
        return result;
    }

    private boolean isInfinite(Interval interval) {
        return interval.min == null || interval.max == null;
    }

    private boolean isZero(Interval interval) {
        boolean a = interval.min != null && interval.max != null && interval.min == 0 && interval.max == 0;
        return interval.min != null && interval.max != null && interval.min == 0 && interval.max == 0;
    }

    private boolean isPositive(String v1) {
        if(v1.equals("B")){
            return false;
        }else if (v1.equals("T")){
            return true;
        }
        Integer value = Integer.parseInt(v1);
        return value > 0;
    }

    private boolean isNegative(String v1) {
        if(v1.equals("T")){
            return false;
        }else if (v1.equals("B")){
            return true;
        }
        Integer value = Integer.parseInt(v1);
        return value < 0;
    }

    private String safeAdd(String a, String b) {
        if(a.equals("B") || b.equals("B")){
            return "B";
        }else if(a.equals("T") || b.equals("T")){
            return "T";
        }else {
            Integer aInteger = Integer.parseInt(a);
            Integer bInteger = Integer.parseInt(b);
            Integer integer = aInteger + bInteger;
            return integer.toString();
        }
    }
    private String safeSubtract(String a, String b) {
        if(a.equals("B") || b.equals("T")){
            return "B";
        }else if(a.equals("T") || b.equals("B")){
            return "T";
        }else {
            Integer aInteger = Integer.parseInt(a);
            Integer bInteger = Integer.parseInt(b);
            Integer integer = aInteger - bInteger;
            return integer.toString();
        }
    }

}

import java.util.Objects;

public class VarInterval{
    boolean isTop = false;
    Integer constantValue = null;
    String pointsTo = null;
    boolean isInt = false;

    Interval interval = Interval.bottom();

    public boolean isInt() {
        return isInt;
    }

    public void setInt(boolean b) {
        isInt = b;
    }

    public void setInterval(Interval interval) {
        this.interval = interval;
    }

    public Interval getInterval() {
        return interval;
    }

    void markAsTop() {
        if(this.pointsTo == null){
            this.isTop = true;
            this.constantValue = null;
        }
    }

    void markAsBottom() {
        if(this.pointsTo == null){
            this.isTop = false;
            this.constantValue = null;
        }
    }

    public void setPointsTo(String pointsTo) {
        this.pointsTo = pointsTo;
        isTop = false;
    }

    void setConstantValue(Integer value) {
        if(value != null) {
//            if(this.interval.isBottom() ) {
//                this.interval.setInterval(value, value);
//            }
            if(!(this.interval.getMin() != null && this.interval.getMax() != null &&this.interval.getMin()==0 && this.interval.getMin()==1)){
                this.interval.setInterval(value, value);
            }
            this.constantValue = value;
            this.isTop = false;
        }
    }

    public boolean isTop() {
        return isTop;
    }
    public boolean isBottom(){
        if(this.isTop) return false;
        if(this.isInt() && !this.hasConstantValue()){
            return true;
        }
        return false;
    }

    public Integer getConstantValue() {
        return constantValue;
    }

    public boolean hasConstantValue() {
        return constantValue != null;
    }

    public String getPointsTo() {
        return pointsTo;
    }

    public void merge(VarInterval predecessorState) {
        this.join(predecessorState);
    }

    @Override
    public VarInterval clone() {
        VarInterval newState = new VarInterval();
        newState.isTop = this.isTop;
        newState.setConstantValue(this.getConstantValue());
        newState.pointsTo = this.pointsTo;
        newState.isInt = this.isInt;
        newState.setInterval(new Interval(this.interval.getMin(), this.interval.getMax()));
        return newState;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VarInterval other = (VarInterval) obj;
        return isTop == other.isTop &&
                Objects.equals(constantValue, other.constantValue) &&
                Objects.equals(pointsTo, other.pointsTo) &&
                isInt == other.isInt;
    }

    public VarInterval join(VarInterval other) {
        VarInterval result = this.clone();

        //should change after worklist
        if (this.isTop || other.isTop) {
            result.markAsTop();
        }else if (Objects.equals(this.constantValue, other.constantValue)) {

        }else if(this.isBottom() && other.hasConstantValue()){
            result.setConstantValue(other.getConstantValue());
        }else if(this.hasConstantValue() && other.isBottom()){

        }else{
            result.markAsTop();
        }
        result.interval = Interval.join(this.interval, other.interval);
        return result;
    }
}
import java.util.Objects;

class VariableState{
    boolean isTop = false;
    Integer constantValue = null;
    String pointsTo = null;
    boolean isInt = false;

    public boolean isInt() {
        return isInt;
    }

    public void setInt(boolean b) {
        isInt = b;
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
        this.constantValue = value;
        this.isTop = false;
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

    public void merge(VariableState predecessorState) {
        this.join(predecessorState);
    }

    @Override
    public VariableState clone() {
        VariableState newState = new VariableState();
        newState.isTop = this.isTop;
        newState.constantValue = this.constantValue;
        newState.pointsTo = this.pointsTo;
        newState.isInt = this.isInt;
        return newState;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        VariableState other = (VariableState) obj;
        return isTop == other.isTop &&
                Objects.equals(constantValue, other.constantValue) &&
                Objects.equals(pointsTo, other.pointsTo) &&
                isInt == other.isInt;
    }

    void weakUpdate(VariableState other) {
        if (this.isTop || other.isTop) {
            this.markAsTop();
        } else if (this.constantValue != null && other.constantValue != null && !Objects.equals(this.constantValue, other.constantValue)) {
            this.markAsTop();
        } else if (this.pointsTo != null && other.pointsTo != null && !Objects.equals(this.pointsTo, other.pointsTo)) {
            this.markAsTop();
        }
    }

    public VariableState join(VariableState other) {
        VariableState result = this.clone();

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

        return result;
    }
}


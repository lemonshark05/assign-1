public class Interval {
    Integer min; // null represents -∞
    Integer max; // null represents ∞


    // Constructor for non-empty intervals. Use null for -∞ or ∞
    public Interval(Integer low, Integer max) {
        this.min = low;
        this.max = max;
    }

    public Interval() {
        this.min = 1;
        this.max = 0;
    }

    // Represents ⊥ (the empty interval)
    public static Interval bottom() {
        return new Interval(1, 0); // Specific representation of ⊥
    }

    // Represents ⊤ ([-∞, ∞])
    public static Interval top() {
        Interval newIn = new Interval(null, null);
        return newIn;
    }

    public Integer getMin() {
        return min;
    }

    public void setMin(Integer min) {
        this.min = min;
    }

    public void setInterval(Integer v1, Integer v2){
        this.min = v1;
        this.max = v2;
    }

    public Integer getMax() {
        return max;
    }

    public void setMax(Integer max) {
        this.max = max;
    }

    // Check if the interval is ⊥
    public boolean isBottom() {
        return min != null && max != null && min > max;
    }

    public boolean isAll() {
        return min == null && max == null;
    }

    // Join of two intervals
    public static Interval join(Interval a, Interval b) {
        if (a.isBottom()) return b;
        if (b.isBottom()) return a;

        Integer newLow = (a.min == null || b.min == null) ? null : Math.min(a.min, b.min);
        Integer newmax = (a.max == null || b.max == null) ? null : Math.max(a.max, b.max);
        return new Interval(newLow, newmax);
    }

    // Widening operation
    public Interval widen(Interval next) {
        if (this.isBottom()) {
            return next;
        }

        Integer newLow = (this.min == null || (next.min != null && this.min <= next.min)) ? this.min : null;
        Integer newmax = (this.max == null || (next.max != null && this.max >= next.max)) ? this.max : null;
        return new Interval(newLow, newmax);
    }

    @Override
    public String toString() {
        return "[" + (min == null ? "-∞" : min) + ", " + (max == null ? "∞" : max) + "]";
    }
}

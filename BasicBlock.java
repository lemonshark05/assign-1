import java.util.ArrayList;
import java.util.List;

public class BasicBlock<State> {
    private State preState;
    private State postState;
    private List<BasicBlock<State>> successors = new ArrayList<>();

    public BasicBlock(State initialState) {
        this.preState = initialState;
        this.postState = initialState;
    }

    public void addSuccessor(BasicBlock<State> successor) {
        successors.add(successor);
    }

    public void setPreState(State preState) {
        this.preState = preState;
    }

    public void setPostState(State postState) {
        this.postState = postState;
    }

    public void setSuccessors(List<BasicBlock<State>> successors) {
        this.successors = successors;
    }

    public State getPreState() {
        return preState;
    }

    public State getPostState() {
        return postState;
    }

    public List<BasicBlock<State>> getSuccessors() {
        return successors;
    }
}
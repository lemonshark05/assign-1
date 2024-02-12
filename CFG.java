import java.util.ArrayList;
import java.util.List;

class CFG {
    List<BasicBlock> blocks = new ArrayList<>();

    public void addBlock(BasicBlock block) {
        blocks.add(block);
    }

    public List<BasicBlock> getBlocks() {
        return blocks;
    }
}
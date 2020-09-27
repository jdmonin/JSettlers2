package soc.robot;

/**
 * Interface for building plans, to be implemented as desired.
 * @author kho30
 *
 */
public interface SOCBuildPlan
{
    /**
     * Reset the build plan
     */
    public void clear();

    /**
     * Return true if nothing is currently planned
     * @return
     */
    public boolean isEmpty();

    /**
     * Get the ith planned build item.
     * This is typically called with an index of 0 (the first piece, equivalent to a peek),
     * however it is called with an index of 1 during the play of a Road Building card.
     *
     * Note: This may be unsafe - assumes an appropriate size of build plan.  Could easily add a check for size in the
     * function call, but I think it's probably easier to debug for now with bad calls throwing exceptions, rather than
     * returning nulls that may be harder to figure out if they create funny behavior later.
     *
     * Non-linear build plans should be discussed as to how to implement this.  I'm imagining we would
     * traverse a tree-like structure, so separate functions would need to be added to switch between branches.
     *
     * @param pieceNum
     * @return
     */
    public SOCPossiblePiece getPlannedPiece(int pieceNum);

    /**
     * Return the depth of the plan.  Non-linear plans to be discussed.
     * @return
     */
    public int getPlanDepth();

    /**
     * Step forward in the plan.  Equivalent to a pop in the stack implementation.
     */
    public void advancePlan();

}

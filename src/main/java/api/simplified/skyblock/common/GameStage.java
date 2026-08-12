package api.simplified.skyblock.common;

/**
 * How far into the game a member has progressed, as a coarse band rather than a level.
 * <p>
 * The bands are this codebase's own, not the game's - nothing on the wire carries them. They exist
 * so advice and comparisons can be scoped to players at a similar point, since the same number means
 * very different things early and late.
 */
public enum GameStage {

    /**
     * Progress that has not been placed into a band.
     */
    UNKNOWN,

    /**
     * A member new to the game, still on the starting island.
     */
    STARTER,

    /**
     * A member past the opening but not yet into any endgame content.
     */
    AMATEUR,

    /**
     * A member working through the middle of the game.
     */
    INTERMEDIATE,

    /**
     * A member comfortable with most of the game's content.
     */
    SKILLED,

    /**
     * A member into the endgame content.
     */
    EXPERT,

    /**
     * A member with the endgame content largely behind them.
     */
    PROFESSIONAL,

    /**
     * A member at the top of the progression, with little left to unlock.
     */
    MASTER

}
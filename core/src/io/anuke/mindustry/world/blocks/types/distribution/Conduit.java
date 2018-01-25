package io.anuke.mindustry.world.blocks.types.distribution;

import io.anuke.mindustry.resource.Liquid;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.types.LiquidBlock;

public class Conduit extends LiquidBlock {

    public Conduit(String name) {
        super(name);
    }

    @Override
    public boolean canReplace(Block other) {
        return other instanceof Conduit && other != this;
    }

    @Override
    public boolean acceptLiquid(Tile tile, Tile source, Liquid liquid, float amount) {
        return super.acceptLiquid(tile, source, liquid, amount) && (!rotate ||
         !(source.relativeTo(tile.x, tile.y) == (tile.getRotation() + 2 % 4)));
    }
}

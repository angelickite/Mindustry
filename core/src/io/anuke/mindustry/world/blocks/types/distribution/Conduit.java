package io.anuke.mindustry.world.blocks.types.distribution;

import io.anuke.mindustry.resource.Liquid;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.types.LiquidAcceptor;
import io.anuke.mindustry.world.blocks.types.LiquidBlock;

//TODO broken and outputs too much
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
        return super.acceptLiquid(tile, source, liquid, amount);
    }

    @Override
    public void update(Tile tile){
        LiquidEntity entity = tile.entity();

        byte rot = tile.getRotation();
        for (int i = 0; i < 4; i++) {
            Tile other = tile.nearby(i);
            if(!(other.block() instanceof LiquidAcceptor)) continue;
            if (!rotate || i == rot || i == (rot + 2) % 4 || (other.block().rotate && other.isPerpedicular(tile.x, tile.y))) {
                tryMoveLiquid(tile, other);
            }
        }

    }
}

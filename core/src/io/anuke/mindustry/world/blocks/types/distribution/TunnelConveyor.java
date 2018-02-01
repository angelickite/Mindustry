package io.anuke.mindustry.world.blocks.types.distribution;

import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.util.Log;

public class TunnelConveyor extends Block{
	protected int maxdist = 3;

	protected TunnelConveyor(String name) {
		super(name);
		rotate = true;
		update = true;
		solid = true;
		health = 70;
	}
	
	@Override
	public boolean canReplace(Block other){
		return other instanceof Conveyor || other instanceof Router || other instanceof Junction;
	}
	
	@Override
	public synchronized void handleItem(Item item, Tile tile, Tile source){
		Tile tunnel = getDestTunnel(tile, item);
		if(tunnel == null) return;
		Tile to = tunnel.getNearby()[tunnel.getRotation()];
		if(to == null) return;
		Block before = to.block();
		
		Timers.run(25, () -> {
			if(to.block() != before) return;
			//TODO fix
			try {
				to.block().handleItem(item, to, tunnel);
			}catch (NullPointerException e){
				Log.err(e);
			}
		});
	}

	@Override
	public synchronized boolean acceptItem(Item item, Tile tile, Tile source){
		int rot = source.relativeTo(tile.x, tile.y);
		if(rot != (tile.getRotation() + 2)%4) return false;
		Tile tunnel = getDestTunnel(tile, item);

		if(tunnel != null){
			Tile to = tunnel.getNearby()[tunnel.getRotation()];
			return to != null && !(to.block() instanceof TunnelConveyor) && to.block().acceptItem(item, to, tunnel);
		}else{
			return false;
		}
	}
	
	synchronized Tile getDestTunnel(Tile tile, Item item){
		Tile dest = tile;
		int rel = (tile.getRotation() + 2)%4;
		for(int i = 0; i < maxdist; i ++){
			dest = dest.getNearby()[rel];
			if(dest != null && dest.block() instanceof TunnelConveyor && dest.getRotation() == rel
					&& dest.getNearby(temptiles)[rel] != null
					&& dest.getNearby(temptiles)[rel].block().acceptItem(item, dest.getNearby(temptiles)[rel], dest)){
				return dest;
			}
		}
		return null;
	}
}

package io.anuke.mindustry.world.blocks.types.distribution;

import io.anuke.mindustry.entities.TileEntity;
import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.world.Block;
import io.anuke.mindustry.world.Tile;
import io.anuke.ucore.core.Timers;

public class TunnelConveyor extends Block{
	protected int maxdist = 3;
	protected float speed = 20; //frames taken to go through this tunnel
	protected int capacity = 16;

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
	public void handleItem(Item item, Tile tile, Tile source){
		TunnelEntity entity = tile.entity();

		Tile tunnel = getDestTunnel(tile, item);
		if(tunnel == null) return;
		Tile to = tunnel.getNearby(tunnel.getRotation());
		if(to == null) return;

		entity.buffer[entity.index ++] = item.id;
	}

	@Override
	public void update(Tile tile){
		TunnelEntity entity = tile.entity();

		if(entity.index > 0){
			entity.time += Timers.delta();
			if(entity.time >= speed){
				int i = entity.buffer[entity.index - 1];

				Item item = Item.getByID(i);

				Tile tunnel = getDestTunnel(tile, item);
				if(tunnel == null) return;
				Tile target = tunnel.getNearby(tunnel.getRotation());
				if(target == null) return;

				if(!target.block().acceptItem(item, target, tunnel)) return;

				target.block().handleItem(item, target, tunnel);

				entity.index --;
				entity.time = 0f;
			}
		}else{
			entity.time = 0f;
		}
	}

	@Override
	public boolean acceptItem(Item item, Tile tile, Tile source){
		TunnelEntity entity = tile.entity();

		if(entity.index >= entity.buffer.length - 1) return false;

		int rot = source.relativeTo(tile.x, tile.y);
		if(rot != (tile.getRotation() + 2)%4) return false;
		Tile tunnel = getDestTunnel(tile, item);

		if(tunnel != null){
			Tile to = tunnel.getNearby(tunnel.getRotation());
			return to != null && !(to.block() instanceof TunnelConveyor) && to.block().acceptItem(item, to, tunnel);
		}else{
			return false;
		}
	}

	@Override
	public TileEntity getEntity() {
		return new TunnelEntity();
	}

	Tile getDestTunnel(Tile tile, Item item){
		Tile dest = tile;
		int rel = (tile.getRotation() + 2)%4;
		for(int i = 0; i < maxdist; i ++){
			dest = dest.getNearby(rel);
			if(dest != null && dest.block() instanceof TunnelConveyor && dest.getRotation() == rel
					&& dest.getNearby(rel) != null
					&& dest.getNearby(rel).block().acceptItem(item, dest.getNearby(rel), dest)){
				return dest;
			}
		}
		return null;
	}

	class TunnelEntity extends TileEntity {
		int[] buffer = new int[capacity];
		int index;
		float time;
	}
}

package io.anuke.mindustry.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.IntSet;
import com.badlogic.gdx.utils.TimeUtils;
import io.anuke.mindustry.core.GameState.State;
import io.anuke.mindustry.entities.Bullet;
import io.anuke.mindustry.entities.BulletType;
import io.anuke.mindustry.entities.Player;
import io.anuke.mindustry.entities.SyncEntity;
import io.anuke.mindustry.entities.enemies.Enemy;
import io.anuke.mindustry.io.Platform;
import io.anuke.mindustry.net.Net;
import io.anuke.mindustry.net.Net.SendMode;
import io.anuke.mindustry.net.NetworkIO;
import io.anuke.mindustry.net.Packets.*;
import io.anuke.mindustry.resource.Item;
import io.anuke.mindustry.world.Map;
import io.anuke.mindustry.world.Tile;
import io.anuke.mindustry.world.blocks.ProductionBlocks;
import io.anuke.ucore.core.Timers;
import io.anuke.ucore.entities.BaseBulletType;
import io.anuke.ucore.entities.Entities;
import io.anuke.ucore.entities.Entity;
import io.anuke.ucore.entities.EntityGroup;
import io.anuke.ucore.modules.Module;
import io.anuke.ucore.util.Log;

import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static io.anuke.mindustry.Vars.*;

public class NetClient extends Module {
    boolean connecting = false;
    boolean gotData = false;
    boolean kicked = false;
    IntSet recieved = new IntSet();
    float playerSyncTime = 2;
    float dataTimeout = 60*18; //18 seconds timeout

    public NetClient(){

        Net.handleClient(Connect.class, packet -> {
            Net.setClientLoaded(false);
            recieved.clear();
            connecting = true;
            gotData = false;
            kicked = false;

            ui.chatfrag.clearMessages();
            ui.loadfrag.hide();
            ui.loadfrag.show("$text.connecting.data");

            Entities.clear();

            ConnectPacket c = new ConnectPacket();
            c.name = player.name;
            c.android = android;
            c.color = Color.rgba8888(player.color);
            Net.send(c, SendMode.tcp);

            Timers.runTask(dataTimeout, () -> {
                if (!gotData) {
                    Gdx.app.error("Mindustry", "Failed to load data!");
                    ui.loadfrag.hide();
                    Net.disconnect();
                }
            });
        });

        Net.handleClient(Disconnect.class, packet -> {
            if (kicked) return;

            Timers.runFor(3f, ui.loadfrag::hide);

            state.set(State.menu);

            ui.showError("$text.disconnect");
            connecting = false;

            Platform.instance.updateRPC();
        });

        Net.handleClient(WorldData.class, data -> {
            Log.info("Recieved world data: {0} bytes.", data.stream.available());
            NetworkIO.loadWorld(data.stream);
            player.set(world.getSpawnX(), world.getSpawnY());

            gotData = true;

            finishConnecting();
        });

        Net.handleClient(CustomMapPacket.class, packet -> {
            Log.info("Recieved custom map: {0} bytes.", packet.stream.available());

            //custom map is always sent before world data
            Map map = NetworkIO.loadMap(packet.stream);

            world.maps().setNetworkMap(map);

            MapAckPacket ack = new MapAckPacket();
            Net.send(ack, SendMode.tcp);
        });

        Net.handleClient(SyncPacket.class, packet -> {
            if (!gotData) return;
            int players = 0;
            int enemies = 0;

            ByteBuffer data = ByteBuffer.wrap(packet.data);
            long time = data.getLong();

            byte groupid = data.get();

            EntityGroup<?> group = Entities.getGroup(groupid);

            while (data.position() < data.capacity()) {
                int id = data.getInt();

                SyncEntity entity = (SyncEntity) group.getByID(id);

                if(entity instanceof Player) players ++;
                if(entity instanceof Enemy) enemies ++;

                if (entity == null || id == player.id) {
                    if (id != player.id) {
                        EntityRequestPacket req = new EntityRequestPacket();
                        req.id = id;
                        req.group = groupid;
                        Net.send(req, SendMode.udp);
                    }
                    data.position(data.position() + SyncEntity.getWriteSize((Class<? extends SyncEntity>) group.getType()));
                } else {
                    entity.read(data, time);
                }
            }

            if(debugNet){
                clientDebug.setSyncDebug(players, enemies);
            }
        });

        Net.handleClient(StateSyncPacket.class, packet -> {

            System.arraycopy(packet.items, 0, state.inventory.getItems(), 0, packet.items.length);

            state.enemies = packet.enemies;
            state.wavetime = packet.countdown;
            state.wave = packet.wave;

            Timers.resetTime(packet.time + (float) (TimeUtils.timeSinceMillis(packet.timestamp) / 1000.0 * 60.0));

            ui.hudfrag.updateItems();
        });

        Net.handleClient(EntitySpawnPacket.class, packet -> {
            EntityGroup group = packet.group;

            //duplicates.
            if (group.getByID(packet.entity.id) != null ||
                    recieved.contains(packet.entity.id)) return;

            recieved.add(packet.entity.id);

            packet.entity.add();

            Log.info("Recieved entity {0}", packet.entity.id);
        });

        Net.handleClient(EnemyDeathPacket.class, packet -> {
            Enemy enemy = enemyGroup.getByID(packet.id);
            if (enemy != null){
                enemy.type.onDeath(enemy, true);
            }else{
                Log.err("Got remove for null entity! {0}", packet.id);
            }
            recieved.add(packet.id);
        });

        Net.handleClient(BulletPacket.class, packet -> {
            //TODO shoot effects for enemies, clientside as well as serverside
            BulletType type = (BulletType) BaseBulletType.getByID(packet.type);
            Entity owner = enemyGroup.getByID(packet.owner);
            new Bullet(type, owner, packet.x, packet.y, packet.angle).add();
        });

        Net.handleClient(BlockDestroyPacket.class, packet -> {
            Tile tile = world.tile(packet.position % world.width(), packet.position / world.width());
            if (tile != null && tile.entity != null) {
                tile.entity.onDeath(true);
            }
        });

        Net.handleClient(BlockUpdatePacket.class, packet -> {
            Tile tile = world.tile(packet.position % world.width(), packet.position / world.width());
            if (tile != null && tile.entity != null) {
                tile.entity.health = packet.health;
            }
        });

        Net.handleClient(BlockSyncPacket.class, packet -> {
            if (!gotData) return;

            DataInputStream stream = new DataInputStream(packet.stream);

            try {

                float time = stream.readFloat();
                float elapsed = Timers.time() - time;

                while (stream.available() > 0) {
                    int pos = stream.readInt();

                    //TODO what if there's no entity? new code
                    Tile tile = world.tile(pos % world.width(), pos / world.width());

                    byte times = stream.readByte();

                    for (int i = 0; i < times; i++) {
                        tile.entity.timer.getTimes()[i] = stream.readFloat();
                    }

                    short data = stream.readShort();
                    tile.setPackedData(data);

                    tile.entity.readNetwork(stream, elapsed);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                Log.err(e);
                //do nothing else...
                //TODO fix
            }

        });

        Net.handleClient(DisconnectPacket.class, packet -> {
            Player player = playerGroup.getByID(packet.playerid);

            if (player != null) {
                player.remove();
            }

            Platform.instance.updateRPC();
        });

        Net.handleClient(KickPacket.class, packet -> {
            kicked = true;
            Net.disconnect();
            state.set(State.menu);
            ui.showError("$text.server.kicked." + packet.reason.name());
            ui.loadfrag.hide();
        });

        Net.handleClient(GameOverPacket.class, packet -> {
            if(world.getCore().block() != ProductionBlocks.core &&
                    world.getCore().entity != null){
                world.getCore().entity.onDeath(true);
            }
            kicked = true;
            ui.restart.show();
        });

        Net.handleClient(FriendlyFireChangePacket.class, packet -> state.friendlyFire = packet.enabled);

        Net.handleClient(ItemTransferPacket.class, packet -> {
            Tile tile = world.tile(packet.position);
            if(tile == null || tile.entity == null) return;
            Tile next = tile.getNearby(packet.rotation);
            tile.entity.items[packet.itemid] --;
            next.block().handleItem(Item.getByID(packet.itemid), next, tile);
        });
    }

    @Override
    public void update(){
        if(!Net.client()) return;

        if(!state.is(State.menu)){
            if(gotData) sync();
        }else if(!connecting){
            Net.disconnect();
        }
    }

    //TODO remove.
    public void test(){
        gotData = false;
        connecting = true;
    }

    public boolean hasData(){
        return gotData;
    }

    public boolean isConnecting(){
        return connecting;
    }

    private void finishConnecting(){
        Net.send(new ConnectConfirmPacket(), SendMode.tcp);
        state.set(State.playing);
        connecting = false;
        ui.loadfrag.hide();
        ui.join.hide();
        Net.setClientLoaded(true);
    }

    public void beginConnecting(){
        connecting = true;
    }

    public void disconnectQuietly(){
        kicked = true;
        Net.disconnect();
    }

    public void clearRecieved(){
        recieved.clear();
    }

    void sync(){
        if(Timers.get("syncPlayer", playerSyncTime)){
            byte[] bytes = new byte[player.getWriteSize() + 8];
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            buffer.putLong(TimeUtils.millis());
            player.write(buffer);

            PositionPacket packet = new PositionPacket();
            packet.data = bytes;
            Net.send(packet, SendMode.udp);
        }

        if(Timers.get("updatePing", 60)){
            Net.updatePing();
        }
    }
}

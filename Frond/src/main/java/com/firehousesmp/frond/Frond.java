package com.firehousesmp.frond;


import java.util.List;
import java.util.Set;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.Tag;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;


public final class Frond extends JavaPlugin implements Listener {
    // Plugin startup logic
    private final Set<String> inWorlds = new HashSet<>();
    private final Set<String> notInWorlds = new HashSet<>();
    private long breakDelay;
    private long decayDelay;
    private boolean spawnParticles;
    private boolean playSound;
    private boolean oneXOne;
    private final List<Block> scheduledBlocks = new ArrayList<>();
    private static final List<BlockFace> NEIGHBORS = Arrays
            .asList(BlockFace.UP, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.DOWN);


    @Override
    public void onEnable() {
        //Load the configurations
        reloadConfig();
        saveDefaultConfig();
        inWorlds.addAll(getConfig().getStringList("inWorlds"));
        notInWorlds.addAll(getConfig().getStringList("notInWorlds"));
        breakDelay = getConfig().getLong("breakDelay");
        decayDelay = getConfig().getLong("decayDelay");
        oneXOne = getConfig().getBoolean("oneXOne");
        spawnParticles = getConfig().getBoolean("spawnParticles");
        playSound = getConfig().getBoolean("PlaySound");
        // Now Register the events
        getServer().getPluginManager().registerEvents(this, this);

    }

    @Override
    public void onDisable() {
        //Clean up this mess
        scheduledBlocks.clear();
    }


    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event){
        onBlockRemove(event.getBlock(), breakDelay);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onLeavesDecay(LeavesDecayEvent event){
        onBlockRemove(event.getBlock(), decayDelay);
    }

    private void onBlockRemove(final Block oldBlock, long delay){
        if (!Tag.LOGS.isTagged(oldBlock.getType())
                && !Tag.LEAVES.isTagged(oldBlock.getType())) {
            return;
        }
        final String worldName = oldBlock.getWorld().getName();
        if(!inWorlds.isEmpty() && !inWorlds.contains(worldName)) return;
        if(notInWorlds.contains(worldName)) return;

        Collections.shuffle(NEIGHBORS);
        for (BlockFace neighborFace: NEIGHBORS) {
            final Block block = oldBlock.getRelative(neighborFace);
            if (!Tag.LEAVES.isTagged(block.getType())) continue;
            Leaves leaves = (Leaves) block.getBlockData();
            if (leaves.isPersistent()) continue;
            if (scheduledBlocks.contains(block)) continue;
            if (oneXOne) {
                if (scheduledBlocks.isEmpty()) {
                    getServer().getScheduler().runTaskLater(this, this::decayFrond, delay);
                }
                scheduledBlocks.add(block);
            } else {
                getServer().getScheduler().runTaskLater(this, () -> decay(block), delay);
            }
            scheduledBlocks.add(block);
        }

    }

    private boolean decay (Block block){
        if (!scheduledBlocks.remove(block)) return false;
        if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4)) return false;
        if (!Tag.LEAVES.isTagged(block.getType())) return false;
        Leaves leaves = (Leaves) block.getBlockData();
        if (leaves.isPersistent()) return false;
        if (leaves.getDistance() < 7) return false;
        LeavesDecayEvent event = new LeavesDecayEvent(block);
        getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) return false;
        if (spawnParticles) {
            block.getWorld().spawnParticle(Particle.BLOCK_DUST, block.getLocation().add(0.5, 0.5, 0.5), 8, 0.2, 0.2, 0.2, 0, block.getType().createBlockData());
        }
        if (playSound) {
            block.getWorld().playSound(block.getLocation(), Sound.BLOCK_GRASS_BREAK, SoundCategory.BLOCKS, 0.05f, 1.2f);
        }
        block.breakNaturally();
        return true;
    }

    private void decayFrond() {
        boolean decayed = false;
        do {
            if (scheduledBlocks.isEmpty()) return;
            Block block = scheduledBlocks.get(0);
            decayed = decay(block);
        } while (!decayed);
        if (!scheduledBlocks.isEmpty()){
            long delay = decayDelay;
            if (delay <= 0) delay = 1L;
            getServer().getScheduler().runTaskLater(this, this::decayFrond, delay);
        }
    }


}

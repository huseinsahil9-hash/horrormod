package id.horrormod;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;

public class HorrorMod implements ModInitializer {
    public static final String MODID = "horrormod";
    public static final Identifier JUMPSCARE_PACKET = new Identifier(MODID, "jumpscare");

    public static final EntityType<StalkerEntity> STALKER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MODID, "stalker"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, StalkerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 2.3f))
                    .trackRangeBlocks(64)
                    .build());

    private static final SoundEvent[] CREEPY = {
            SoundEvents.ENTITY_PHANTOM_AMBIENT,
            SoundEvents.ENTITY_ELDER_GUARDIAN_AMBIENT,
            SoundEvents.ENTITY_WITHER_AMBIENT,
            SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT,
            SoundEvents.ENTITY_GHAST_AMBIENT,
            SoundEvents.ENTITY_ENDERMAN_STARE,
            SoundEvents.BLOCK_WOODEN_DOOR_OPEN,
            SoundEvents.ENTITY_WARDEN_HEARTBEAT
    };

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STALKER, StalkerEntity.createAttributes());

        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (world.getTime() % 20 != 0) return;
            Random r = world.getRandom();
            for (ServerPlayerEntity p : world.getPlayers()) {
                if (p.isSpectator()) continue;

                // Suara seram dan suara tiruan acak di sekitar pemain
                if (r.nextInt(20) == 0) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double d = 6 + r.nextInt(8);
                    SoundEvent s = r.nextBoolean()
                            ? CREEPY[r.nextInt(CREEPY.length)]
                            : StalkerEntity.MIMIC[r.nextInt(StalkerEntity.MIMIC.length)];
                    world.playSound(null,
                            p.getX() + Math.cos(ang) * d, p.getY(), p.getZ() + Math.sin(ang) * d,
                            s, SoundCategory.AMBIENT, 1.0f, 0.5f + r.nextFloat() * 0.3f);
                }

                // Stalker muncul di tempat gelap (jenis diacak: pengintai atau pemburu)
                boolean dark = world.getLightLevel(p.getBlockPos()) < 6;
                if (dark && r.nextInt(150) == 0
                        && world.getEntitiesByType(STALKER, p.getBoundingBox().expand(64), e -> true).isEmpty()) {
                    spawnNear(world, p, true, false);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("stalker")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> runSpawn(ctx.getSource(), true, false))
                        .then(CommandManager.literal("hunt")
                                .executes(ctx -> runSpawn(ctx.getSource(), true, true)))
                        .then(CommandManager.literal("near")
                                .executes(ctx -> runSpawn(ctx.getSource(), false, true)))));
    }

    private static int runSpawn(ServerCommandSource src, boolean far, boolean hunter) throws CommandSyntaxException {
        ServerPlayerEntity p = src.getPlayerOrThrow();
        boolean ok = spawnNear(p.getServerWorld(), p, far, hunter);
        src.sendFeedback(() -> Text.literal(ok ? "Stalker muncul..." : "Tidak ada tempat untuk spawn"), false);
        return ok ? 1 : 0;
    }

    // Cari tempat berdiri yang aman di sekitar pemain pada arah tertentu
    public static BlockPos findSpot(ServerWorld world, PlayerEntity p, double angle, int minDist, int maxDist) {
        Random r = world.getRandom();
        for (int i = 0; i < 14; i++) {
            double a = angle + (r.nextDouble() - 0.5) * 1.0;
            double dist = minDist + r.nextInt(Math.max(1, maxDist - minDist));
            int x = MathHelper.floor(p.getX() + Math.cos(a) * dist);
            int z = MathHelper.floor(p.getZ() + Math.sin(a) * dist);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = new BlockPos(x, p.getBlockY() + dy, z);
                if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
                        && world.isAir(pos) && world.isAir(pos.up())) {
                    return pos;
                }
            }
        }
        return null;
    }

    // far = true: di depan pemain pada jarak 28-38 blok. far = false: dekat di belakang pemain.
    // hunter = true: dipaksa jadi pemburu (menyerang).
    public static boolean spawnNear(ServerWorld world, ServerPlayerEntity p, boolean far, boolean hunter) {
        Random r = world.getRandom();
        BlockPos spot;
        if (far) {
            spot = findSpot(world, p, Math.toRadians(p.getYaw() + 90), 26, 34);
            if (spot == null) spot = findSpot(world, p, r.nextDouble() * Math.PI * 2, 24, 34);
        } else {
            spot = findSpot(world, p, Math.toRadians(p.getYaw() + 270), 14, 22);
            if (spot == null) spot = findSpot(world, p, r.nextDouble() * Math.PI * 2, 12, 20);
        }
        if (spot == null) return false;

        StalkerEntity e = STALKER.create(world);
        if (e == null) return false;
        e.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, r.nextFloat() * 360f, 0f);
        if (!far) e.skipWatch();
        if (hunter) e.makeHunter();
        world.spawnEntity(e);
        world.playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.5f, 0.8f);
        return true;
    }
                                  }

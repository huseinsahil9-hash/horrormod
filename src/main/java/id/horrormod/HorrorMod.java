package id.horrormod;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
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

    public static final EntityType<StalkerEntity> STALKER = Registry.register(
            Registries.ENTITY_TYPE,
            new Identifier(MODID, "stalker"),
            FabricEntityTypeBuilder.create(SpawnGroup.MONSTER, StalkerEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.95f))
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

                // Suara seram acak di sekitar pemain
                if (r.nextInt(20) == 0) {
                    double ang = r.nextDouble() * Math.PI * 2;
                    double d = 6 + r.nextInt(8);
                    world.playSound(null,
                            p.getX() + Math.cos(ang) * d, p.getY(), p.getZ() + Math.sin(ang) * d,
                            CREEPY[r.nextInt(CREEPY.length)], SoundCategory.AMBIENT,
                            1.0f, 0.5f + r.nextFloat() * 0.3f);
                }

                // Stalker muncul di tempat gelap
                boolean dark = world.getLightLevel(p.getBlockPos()) < 6;
                if (dark && r.nextInt(120) == 0
                        && world.getEntitiesByType(STALKER, p.getBoundingBox().expand(64), e -> true).isEmpty()) {
                    spawnNear(world, p);
                }
            }
        });

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("stalker")
                        .requires(src -> src.hasPermissionLevel(2))
                        .executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            boolean ok = spawnNear(p.getServerWorld(), p);
                            ctx.getSource().sendFeedback(
                                    () -> Text.literal(ok ? "Stalker muncul..." : "Tidak ada tempat untuk spawn"), false);
                            return ok ? 1 : 0;
                        })));
    }

    public static boolean spawnNear(ServerWorld world, ServerPlayerEntity p) {
        Random r = world.getRandom();
        for (int i = 0; i < 16; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 14 + r.nextInt(10);
            int x = MathHelper.floor(p.getX() + Math.cos(ang) * dist);
            int z = MathHelper.floor(p.getZ() + Math.sin(ang) * dist);
            for (int dy = 4; dy >= -4; dy--) {
                BlockPos pos = new BlockPos(x, p.getBlockY() + dy, z);
                if (world.getBlockState(pos.down()).isSideSolidFullSquare(world, pos.down(), Direction.UP)
                        && world.isAir(pos) && world.isAir(pos.up())) {
                    StalkerEntity e = STALKER.create(world);
                    if (e == null) return false;
                    e.refreshPositionAndAngles(x + 0.5, pos.getY(), z + 0.5, r.nextFloat() * 360f, 0f);
                    e.setTarget(p);
                    world.spawnEntity(e);
                    world.playSound(null, p.getX(), p.getY(), p.getZ(),
                            SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.5f, 0.8f);
                    return true;
                }
            }
        }
        return false;
    }
                  }

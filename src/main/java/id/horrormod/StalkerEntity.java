package id.horrormod;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class StalkerEntity extends HostileEntity {

    private static final TrackedData<Integer> STATE =
            DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> VARIANT =
            DataTracker.registerData(StalkerEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final int STALK = 0;
    public static final int CRAWL = 1;
    public static final int TILT = 2;
    public static final int CHASE = 3;

    // Suara tiruan (mimic)
    public static final SoundEvent[] MIMIC = {
            SoundEvents.ENTITY_VILLAGER_AMBIENT,
            SoundEvents.ENTITY_VILLAGER_NO,
            SoundEvents.ENTITY_COW_AMBIENT,
            SoundEvents.ENTITY_PIG_AMBIENT,
            SoundEvents.ENTITY_SHEEP_AMBIENT,
            SoundEvents.ENTITY_CAT_AMBIENT,
            SoundEvents.ENTITY_CREEPER_PRIMED,
            SoundEvents.ENTITY_SKELETON_AMBIENT,
            SoundEvents.ENTITY_ZOMBIE_AMBIENT,
            SoundEvents.BLOCK_CHEST_OPEN,
            SoundEvents.BLOCK_WOODEN_DOOR_CLOSE
    };

    private int stateTicks = 0;
    private int lostTicks = 0;
    private int blinkCooldown = 100;
    private int noPlayerTicks = 0;
    private int watchLeft = 0;
    private boolean hunter = false;

    public StalkerEntity(EntityType<? extends StalkerEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
        if (!world.isClient) {
            // 40% versi berdiri tanpa kepala, 60% berkepala
            this.dataTracker.set(VARIANT, this.random.nextInt(100) < 40 ? 1 : 0);
            // Fase menatap dari jauh: 20 sampai 50 detik
            this.watchLeft = 400 + this.random.nextInt(600);
            // 50% pemburu (menyerang), 50% pengintai (menghilang)
            this.hunter = this.random.nextBoolean();
        }
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 64.0);
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(STATE, STALK);
        this.dataTracker.startTracking(VARIANT, 0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 24.0f));
    }

    public int getState() {
        return this.dataTracker.get(STATE);
    }

    public boolean isUpright() {
        return this.dataTracker.get(VARIANT) == 1;
    }

    // Lewati fase menatap (dipakai command /stalker near)
    public void skipWatch() {
        this.watchLeft = 0;
    }

    // Paksa jadi pemburu (dipakai command /stalker hunt dan near)
    public void makeHunter() {
        this.hunter = true;
    }

    private void setState(int s) {
        this.dataTracker.set(STATE, s);
        this.stateTicks = 0;
        if (s == CRAWL) {
            emit(SoundEvents.ENTITY_ZOMBIE_VILLAGER_AMBIENT, 1.5f, 0.5f);
        } else if (s == TILT) {
            emit(SoundEvents.ENTITY_RAVAGER_ROAR, 2.0f, 0.6f);
            emit(SoundEvents.ENTITY_ENDERMAN_SCREAM, 1.5f, 0.5f);
        } else if (s == CHASE) {
            emit(SoundEvents.ENTITY_WARDEN_ROAR, 1.5f, 1.0f);
        }
    }

    private void emit(SoundEvent sound, float volume, float pitch) {
        this.getWorld().playSound(null, this.getX(), this.getY(), this.getZ(),
                sound, SoundCategory.HOSTILE, volume, pitch);
    }

    private void heartbeat(PlayerEntity p, float pitch) {
        this.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(),
                SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.5f, pitch);
    }

    private boolean isLookedAt(PlayerEntity p) {
        Vec3d look = p.getRotationVec(1.0f).normalize();
        Vec3d to = this.getEyePos().subtract(p.getEyePos()).normalize();
        return look.dotProduct(to) > 0.88 && p.canSee(this);
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world.isClient) return;

        if (this.age > 7200) {
            vanish();
            return;
        }

        PlayerEntity target = world.getClosestPlayer(this.getX(), this.getY(), this.getZ(), 64.0, false);
        if (target == null) {
            if (++noPlayerTicks > 200) vanish();
            return;
        }
        noPlayerTicks = 0;

        double dist = this.distanceTo(target);
        boolean observed = dist < 48 && isLookedAt(target);
        int state = getState();
        this.stateTicks++;
        if (this.blinkCooldown > 0) this.blinkCooldown--;
        this.getLookControl().lookAt(target, 40.0f, 40.0f);

        switch (state) {
            case STALK -> {
                if (this.random.nextInt(160) == 0) {
                    emit(MIMIC[this.random.nextInt(MIMIC.length)], 1.2f,
                            0.6f + this.random.nextFloat() * 0.3f);
                }

                if (this.watchLeft > 0) {
                    // Fase menatap dari jauh: diam di tempat
                    this.watchLeft--;
                    this.getNavigation().stop();

                    if (!this.hunter) {
                        // Pengintai: hilang kalau didekati atau waktunya habis
                        if (dist < 16 || this.watchLeft == 0) {
                            vanish();
                            return;
                        }
                    } else {
                        // Pemburu: begitu dilihat, langsung berubah bentuk lalu menyerang
                        if (dist < 30 && this.age % 40 == 0) heartbeat(target, 0.9f);
                        if (observed && dist < 40) {
                            setState(TILT);
                        }
                    }
                } else {
                    // Pemburu yang tidak pernah dilihat: mendekat saat tidak diperhatikan
                    if (dist < 20 && this.age % 40 == 0) heartbeat(target, 0.9f);

                    if (observed) {
                        this.getNavigation().stop();
                        if (dist > 30) {
                            if (this.blinkCooldown <= 0) {
                                this.blinkCooldown = 60;
                                blink(target);
                            }
                        } else {
                            setState(TILT);
                        }
                    } else {
                        if (this.age % 5 == 0) this.getNavigation().startMovingTo(target, 0.6);
                        if (this.blinkCooldown <= 0) {
                            this.blinkCooldown = 80 + this.random.nextInt(120);
                            if (dist > 10 && this.random.nextInt(3) == 0) blink(target);
                        }
                        if (dist < 2.5) jumpscare(target);
                    }
                }
            }
            case CRAWL -> {
                this.getNavigation().stop();
                if (this.stateTicks >= 30) setState(TILT);
            }
            case TILT -> {
                this.getNavigation().stop();
                if (this.stateTicks >= 25) setState(CHASE);
            }
            case CHASE -> {
                if (this.age % 5 == 0) this.getNavigation().startMovingTo(target, 1.1);
                if (this.stateTicks % 10 == 0) heartbeat(target, 1.4f);
                if (this.stateTicks % 30 == 0) emit(SoundEvents.ENTITY_PHANTOM_SWOOP, 1.5f, 0.5f);
                if (this.stateTicks % 60 == 0) emit(SoundEvents.ENTITY_ENDERMAN_SCREAM, 1.0f, 0.5f);

                if (this.canSee(target)) this.lostTicks = 0;
                else this.lostTicks++;

                if (dist < 2.2) {
                    jumpscare(target);
                } else if (this.lostTicks > 240 || this.stateTicks > 900) {
                    vanish();
                }
            }
            default -> {
            }
        }
    }

    private void jumpscare(PlayerEntity target) {
        // Mematikan (Totem of Undying masih bisa menyelamatkan)
        target.damage(this.getDamageSources().mobAttack(this), 1000.0f);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
        World world = this.getWorld();
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.5f);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ENDERMAN_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.6f);
        world.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.HOSTILE, 1.0f, 0.8f);
        if (target instanceof ServerPlayerEntity sp) {
            ServerPlayNetworking.send(sp, HorrorMod.JUMPSCARE_PACKET, PacketByteBufs.create());
        }
        vanish();
    }

    private void blink(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld sw)) return;
        BlockPos spot = HorrorMod.findSpot(sw, target,
                Math.toRadians(target.getYaw() + 270), 10, 18);
        if (spot == null) return;
        sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(),
                15, 0.3, 0.7, 0.3, 0.02);
        this.refreshPositionAndAngles(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5,
                this.random.nextFloat() * 360f, 0f);
        this.getNavigation().stop();
    }

    private void vanish() {
        if (this.getWorld() instanceof ServerWorld sw) {
            sw.spawnParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 1.0, this.getZ(),
                    25, 0.3, 0.7, 0.3, 0.02);
            sw.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0f, 0.5f);
        }
        this.discard();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.ENTITY_ENDERMAN_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.ENTITY_ENDERMAN_DEATH;
    }
                    }

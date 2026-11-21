package id.horrormod;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.world.World;

public class StalkerEntity extends HostileEntity {

    public StalkerEntity(EntityType<? extends StalkerEntity> type, World world) {
        super(type, world);
        this.experiencePoints = 10;
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.36)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 5.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 48.0);
    }

    @Override
    protected void initGoals() {
        this.goalSelector.add(1, new SwimGoal(this));
        this.goalSelector.add(2, new MeleeAttackGoal(this, 1.0, false));
        this.goalSelector.add(5, new WanderAroundFarGoal(this, 0.8));
        this.goalSelector.add(6, new LookAtEntityGoal(this, PlayerEntity.class, 16.0f));
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
    }

    @Override
    public void tick() {
        super.tick();
        World world = this.getWorld();
        if (world.isClient) return;

        // Menghilang sendiri setelah sekitar 4 menit
        if (this.age > 4800) {
            this.discard();
            return;
        }

        // Detak jantung untuk pemain terdekat
        if (this.age % 25 == 0) {
            PlayerEntity near = world.getClosestPlayer(this, 24.0);
            if (near != null) {
                world.playSound(null, near.getX(), near.getY(), near.getZ(),
                        SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.AMBIENT, 1.2f, 1.0f);
            }
        }

        // Suara seram dari si Stalker
        if (this.age % 120 == 60) {
            world.playSound(null, this.getX(), this.getY(), this.getZ(),
                    SoundEvents.ENTITY_PHANTOM_AMBIENT, SoundCategory.HOSTILE, 1.5f, 0.5f);
        }
    }

    @Override
    public boolean tryAttack(Entity target) {
        boolean hit = super.tryAttack(target);
        if (hit && target instanceof LivingEntity living) {
            living.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
            this.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_GHAST_SCREAM, SoundCategory.HOSTILE, 1.0f, 0.6f);
        }
        return hit;
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

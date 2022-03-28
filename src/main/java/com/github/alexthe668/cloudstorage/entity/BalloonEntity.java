package com.github.alexthe668.cloudstorage.entity;

import com.github.alexthe668.cloudstorage.client.particle.CSParticleRegistry;
import com.github.alexthe668.cloudstorage.item.CSItemRegistry;
import com.github.alexthe668.cloudstorage.misc.CSWorldData;
import com.github.alexthe668.cloudstorage.misc.CloudIndex;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PlayMessages;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class BalloonEntity extends Entity {

    private static final EntityDataAccessor<Integer> BALLOON_COLOR = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> POPPED = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Optional<UUID>> CHILD_UUID = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Integer> CHILD_ID = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STRING_LENGTH = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> CHARGED = SynchedEntityData.defineId(BalloonEntity.class, EntityDataSerializers.BOOLEAN);
    private float popTick = 0.0F;
    private Vec3 randomMoveOffset = null;
    public static final int DEFAULT_STRING_LENGTH = 1;

    public BalloonEntity(EntityType<?> type, Level level) {
        super(type, level);
    }

    public BalloonEntity(PlayMessages.SpawnEntity spawnEntity, Level world) {
        this(CSEntityRegistry.BALLOON.get(), world);
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return MovementEmission.NONE;
    }

    public void tick(){
        super.tick();
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        Entity child = getChild();
        if (!level.isClientSide) {
            this.setDeltaMovement(this.getDeltaMovement().add(0, 0.05F, 0));
            if (child != null) {
                this.entityData.set(CHILD_ID, child.getId());
                if (this.distanceTo(child) > this.getStringLength()) {
                    Vec3 back = child.position().add(0, this.getStringLength(), 0).subtract(this.position());
                    this.setDeltaMovement(this.getDeltaMovement().add(back.scale(0.08F)));
                }
                if(child instanceof BalloonCargoEntity){
                    this.setDeltaMovement(this.getDeltaMovement().add(0, 0.08F, 0));
                }
            }
            if (randomMoveOffset == null || random.nextInt(20) == 0) {
                randomMoveOffset = new Vec3(random.nextFloat() - 0.5F, random.nextFloat() - 0.5F, random.nextFloat() - 0.5F).normalize();
            }
            this.pushEntities();
            if (randomMoveOffset != null) {
                Vec3 add = randomMoveOffset.scale(0.003F);
                this.setDeltaMovement(this.getDeltaMovement().add(add));
            }
            if (!this.level.noCollision(this, this.getBoundingBox())) {
                this.moveTowardsClosestSpace(this.getX(), (this.getBoundingBox().minY + this.getBoundingBox().maxY) / 2.0D, this.getZ());
            }
            this.setDeltaMovement(this.getDeltaMovement().multiply(0.8F, 0.6F, 0.8F));
        }else{
            if(this.isCharged()){
                double d0 = (random.nextFloat() - 0.5F) * this.getBbWidth() + this.getDeltaMovement().x;
                double d1 = (random.nextFloat() - 0.5F) * this.getBbHeight() * 0.5F + this.getDeltaMovement().y;
                double d2 = (random.nextFloat() - 0.5F) * this.getBbWidth() + this.getDeltaMovement().z;
                double dist = 0.2F + random.nextFloat() * 0.2F;
                double d3 = d0 * dist;
                double d4 = d1 * dist;
                double d5 = d2 * dist;
                this.level.addParticle(CSParticleRegistry.STATIC_LIGHTNING, this.getX() + d0, this.getY(1.0F) + d1, this.getZ() + d2, d3, d4, d5);
            }
        }
        this.move(MoverType.SELF, this.getDeltaMovement());
        if(this.getY() > this.level.getMaxBuildHeight() + 10 && random.nextInt(20) == 0){
            if(child instanceof BalloonCargoEntity cargo){
                if(cargo.getPlayerUUID() != null){
                    if(cargo.getContainerSize() > 0){
                        CSWorldData data =CSWorldData.get(level);
                        if(data != null){
                            CloudIndex prev = isCharged() ? data.getPublicCloud(getBalloonColor()) : data.getPrivateCloud(cargo.getPlayerUUID(), getBalloonColor());
                            if(prev != null){
                                prev.resize(prev.getContainerSize() + cargo.getContainerSize());
                                for(ItemStack stack : cargo.getContainerItems()){
                                    prev.getContainer().addItem(stack);
                                }
                            }else{
                                CloudIndex cloud = new CloudIndex(cargo.getPlayerUUID(), getBalloonColor(), cargo.getContainerSize());
                                for(ItemStack stack : cargo.getContainerItems()){
                                    ItemStack add = cloud.getContainer().addItem(stack);
                                    if (!add.isEmpty()) {
                                        cargo.spawnAtLocation(add, 0.5F);
                                    }

                                }
                                if(isCharged()){
                                    data.addPublicCloud(cloud);
                                }else{
                                    data.addPrivateCloud(cloud);
                                }
                            }
                            cargo.discard();
                        }

                    }
                }
            }
            this.setPopped(true);
        }
        if(this.isPopped()){
            this.popTick++;
            if(popTick > 3.0F){
                if(!level.isClientSide){
                    this.level.broadcastEntityEvent(this, (byte) 67);
                    if(this.getStringLength() > DEFAULT_STRING_LENGTH){
                        this.spawnAtLocation(new ItemStack(Items.STRING, this.getStringLength() - DEFAULT_STRING_LENGTH));
                    }
                }else{
                    int color = this.getBalloonColor();
                    float r = (float) (color >> 16 & 255) / 255.0F;
                    float g = (float) (color >> 8 & 255) / 255.0F;
                    float b = (float) (color & 255) / 255.0F;
                    for (int i = 0; i < 5 + random.nextInt(2) + 5; i++) {
                        this.level.addParticle(CSParticleRegistry.BALLOON_SHARD, this.getX(), this.getY(0.5F), this.getZ(), r, g, b);
                    }
                }
                if(child instanceof BalloonTieEntity tie){
                    tie.setBalloonCount(tie.getBalloonCount() - 1);
                }
                this.remove(RemovalReason.KILLED);
            }
        }
    }

    @Override
    public Packet<?> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }


    protected boolean pushEntities() {
        List<Entity> list = this.level.getEntities(this, this.getBoundingBox().inflate(0.5F, 0, 0.5F), EntitySelector.pushableBy(this));
        if (!list.isEmpty()) {
            for(int l = 0; l < list.size(); ++l) {
                Entity entity = list.get(l);
                this.push(entity);
            }
        }
        return !list.isEmpty();
    }

    public void push(Entity p_20293_) {
        if (!this.isPassengerOfSameVehicle(p_20293_)) {
            if (!p_20293_.noPhysics && !this.noPhysics) {
                if(this.isCharged() && p_20293_ instanceof BalloonEntity balloon){
                    balloon.setCharged(true);
                }
                double d0 = p_20293_.getX() - this.getX();
                double d1 = p_20293_.getZ() - this.getZ();
                double d2 = Mth.absMax(d0, d1);
                if (d2 >= (double)0.01F) {
                    d2 = Math.sqrt(d2);
                    d0 /= d2;
                    d1 /= d2;
                    double d3 = 1.0D / d2;
                    if (d3 > 1.0D) {
                        d3 = 1.0D;
                    }

                    d0 *= d3;
                    d1 *= d3;
                    d0 *= (double)0.025F;
                    d1 *= (double)0.025F;
                    if (!this.isVehicle()) {
                       this.push(-d0, 0.0D, -d1);
                    }

                    if (!p_20293_.isVehicle()) {
                    //    p_20293_.push(d0, 0.0D, d1);
                    }
                }

            }
        }
    }

    public boolean isPushable() {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(BALLOON_COLOR, 0XE72929);
        this.entityData.define(POPPED, false);
        this.entityData.define(CHILD_UUID, Optional.empty());
        this.entityData.define(CHILD_ID, -1);
        this.entityData.define(STRING_LENGTH, DEFAULT_STRING_LENGTH);
        this.entityData.define(CHARGED, false);
    }

    public void setBalloonColor(int color) {
        this.entityData.set(BALLOON_COLOR, color);
    }

    public int getBalloonColor() {
        return this.entityData.get(BALLOON_COLOR);
    }

    public void setPopped(boolean popped) {
        this.entityData.set(POPPED, popped);
    }

    public boolean isPopped() {
        return this.entityData.get(POPPED);
    }

    public void setCharged(boolean popped) {
        this.entityData.set(CHARGED, popped);
    }

    public boolean isCharged() {
        return this.entityData.get(CHARGED);
    }

    public void setStringLength(int length) {
        this.entityData.set(STRING_LENGTH, length);
    }

    public int getStringLength() {
        return this.entityData.get(STRING_LENGTH);
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    public Entity getChild() {
        UUID id = getChildId();
        if (id != null && !level.isClientSide) {
            return ((ServerLevel) level).getEntity(id);
        }
        return null;
    }

    @Nullable
    public UUID getChildId() {
        return this.entityData.get(CHILD_UUID).orElse(null);
    }

    public void setChildId(@Nullable UUID uniqueId) {
        this.entityData.set(CHILD_UUID, Optional.ofNullable(uniqueId));
    }

    public Entity getTieForRendering() {
        return this.level.getEntity(this.entityData.get(CHILD_ID));
    }

    public boolean isPickable() {
        return true;
    }

    public boolean hurt(DamageSource source, float f) {
        if (this.isInvulnerableTo(source)) {
            return false;
        } else {
            this.markHurt();
            this.setPopped(true);
            return true;
        }
    }

    public InteractionResult interact(Player player, InteractionHand hand) {
        ItemStack itemstack = player.getItemInHand(hand);
        if(itemstack.is(Items.STRING)){
            this.setStringLength(this.getStringLength() + 1);
            if (!player.getAbilities().instabuild) {
                itemstack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @OnlyIn(Dist.CLIENT)
    public void handleEntityEvent(byte id) {
        if (id == 67) {
            int color = this.getBalloonColor();
            float r = (float) (color >> 16 & 255) / 255.0F;
            float g = (float) (color >> 8 & 255) / 255.0F;
            float b = (float) (color & 255) / 255.0F;
            for (int i = 0; i < 5 + random.nextInt(2) + 5; i++) {
                this.level.addParticle(CSParticleRegistry.BALLOON_SHARD, this.getX(), this.getY(0.5F), this.getZ(), r, g, b);
            }
        } else {
            super.handleEntityEvent(id);
        }
    }

    public float getPopProgress(float partialTick){
        if(popTick == 0){
            return 0;
        }
        return  ((float)popTick + partialTick - 1.0F) / 3.0F;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compound) {
        if (compound.hasUUID("ChildUUID")) {
            this.setChildId(compound.getUUID("ChildUUID"));
        }
        this.setBalloonColor(compound.getInt("BalloonColor"));
        this.setStringLength(compound.getInt("StringLength"));
        this.setCharged(compound.getBoolean("Charged"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compound) {
        if (this.getChildId() != null) {
            compound.putUUID("ChildUUID", this.getChildId());
        }
        compound.putInt("BalloonColor", this.getBalloonColor());
        compound.putInt("StringLength", this.getStringLength());
        compound.putBoolean("Charged", this.isCharged());
    }

    public boolean skipAttackInteraction(Entity entity) {
        if (entity instanceof Player) {
            Player player = (Player)entity;
            return !this.level.mayInteract(player, this.blockPosition()) ? true : this.hurt(DamageSource.playerAttack(player), 0.0F);
        } else {
            return false;
        }
    }

    public ItemStack getPickResult() {
        ItemStack itemstack = new ItemStack(CSItemRegistry.BALLOON.get());
        ((DyeableLeatherItem)itemstack.getItem()).setColor(itemstack, this.getBalloonColor());
        return itemstack;
    }

}
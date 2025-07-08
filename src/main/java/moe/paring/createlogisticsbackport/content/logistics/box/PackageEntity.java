package moe.paring.createlogisticsbackport.content.logistics.box;

import com.simibubi.create.AllEntityTypes;
import com.simibubi.create.AllPackets;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.logistics.chute.ChuteBlock;
import com.simibubi.create.foundation.ponder.PonderWorld;
import com.simibubi.create.foundation.utility.AngleHelper;
import com.simibubi.create.foundation.utility.VecHelper;
import moe.paring.createlogisticsbackport.registry.ExtraEntityTypes;
import moe.paring.createlogisticsbackport.registry.ExtraSoundEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.PlayMessages.SpawnEntity;

import javax.annotation.Nullable;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.List;

public class PackageEntity extends LivingEntity implements IEntityAdditionalSpawnData {

	private Entity originalEntity;
	public ItemStack box;

	public int insertionDelay;

	public Vec3 clientPosition, vec2 = Vec3.ZERO, vec3 = Vec3.ZERO;

	public WeakReference<Player> tossedBy = new WeakReference<>(null);

	@SuppressWarnings("unchecked")
	public PackageEntity(EntityType<?> entityTypeIn, Level worldIn) {
		super((EntityType<? extends LivingEntity>) entityTypeIn, worldIn);
		box = ItemStack.EMPTY;
		setYRot(this.random.nextFloat() * 360.0F);
		setYHeadRot(getYRot());
		yRotO = getYRot();
		insertionDelay = 30;
	}

	public PackageEntity(Level worldIn, double x, double y, double z) {
		this(ExtraEntityTypes.PACKAGE.get(), worldIn);
		this.setPos(x, y, z);
		this.refreshDimensions();
	}

	public static PackageEntity fromDroppedItem(Level world, Entity originalEntity, ItemStack itemstack) {
		PackageEntity packageEntity = ExtraEntityTypes.PACKAGE.get()
			.create(world);

		Vec3 position = originalEntity.position();
		packageEntity.setPos(position);
		packageEntity.setBox(itemstack);
		packageEntity.setDeltaMovement(originalEntity.getDeltaMovement()
			.scale(1.5f));
		packageEntity.originalEntity = originalEntity;

		if (world != null && !world.isClientSide)
			if (ChuteBlock.isChute(world.getBlockState(BlockPos.containing(position.x, position.y + .5f, position.z))))
				packageEntity.setYRot(((int) packageEntity.getYRot()) / 90 * 90);

		return packageEntity;
	}

	public static PackageEntity fromItemStack(Level world, Vec3 position, ItemStack itemstack) {
		PackageEntity packageEntity = ExtraEntityTypes.PACKAGE.get()
			.create(world);
		packageEntity.setPos(position);
		packageEntity.setBox(itemstack);
		return packageEntity;
	}

	@Override
	public ItemStack getPickedResult(HitResult target) {
		return box.copy();
	}

	public static AttributeSupplier.Builder createPackageAttributes() {
		return LivingEntity.createLivingAttributes()
			.add(Attributes.MAX_HEALTH, 5f)
			.add(Attributes.MOVEMENT_SPEED, 1f);
	}

	public static EntityType.Builder<?> build(EntityType.Builder<?> builder) {
		@SuppressWarnings("unchecked")
		EntityType.Builder<PackageEntity> boxBuilder = (EntityType.Builder<PackageEntity>) builder;
		return boxBuilder.setCustomClientFactory(PackageEntity::spawn)
			.sized(1, 1);
	}

	@Override
	public void travel(Vec3 p_213352_1_) {
		super.travel(p_213352_1_);

		if (!level().isClientSide)
			return;
		if (getDeltaMovement().length() < 1 / 128f)
			return;
		if (tickCount >= 20)
			return;

		Vec3 motion = getDeltaMovement().scale(.75f);
		AABB bb = getBoundingBox();
		List<VoxelShape> entityStream = level().getEntityCollisions(this, bb.expandTowards(motion));
		motion = collideBoundingBox(this, motion, bb, level(), entityStream);

		Vec3 clientPos = position().add(motion);
		if (lerpSteps != 0)
			clientPos = VecHelper.lerp(Math.min(1, tickCount / 20f), clientPos, new Vec3(lerpX, lerpY, lerpZ));
		if (tickCount < 5)
			setPos(clientPos.x, clientPos.y, clientPos.z);
		if (tickCount < 20)
			lerpTo(clientPos.x, clientPos.y, clientPos.z, getYRot(), getXRot(), lerpSteps == 0 ? 3 : lerpSteps, true);
	}

	@Override
	public void lerpMotion(double x, double y, double z) {
		setDeltaMovement(getDeltaMovement().add(x, y, z)
			.scale(.5f));
	}

	public String getAddress() {
		return box.getTag()
			.getString("Address");
	}

	@Override
	public void tick() {
		if (firstTick) {
			verifyInitialEntity();
			originalEntity = null;
		}

		if (level() instanceof PonderWorld) {
			setDeltaMovement(getDeltaMovement().add(0, -0.06, 0));
			if (position().y < 0.125)
				discard();
		}

		insertionDelay = Math.min(insertionDelay + 1, 30);
		super.tick();

		if (!PackageItem.isPackage(box))
			discard();
	}

	/*
	 * Forge created package entities even when an ItemEntity is spawned as 'fake'.
	 * See: GiveCommand#giveItem. This method discards the package if it originated
	 * from such a fake item
	 */
	protected void verifyInitialEntity() {
		if (!(originalEntity instanceof ItemEntity itemEntity))
			return;
		CompoundTag nbt = new CompoundTag();
		itemEntity.addAdditionalSaveData(nbt);
		if (nbt.getInt("PickupDelay") != 32767) // See: ItemEntity#makeFakeItem
			return;
		discard();
	}

	@Override
	public EntityDimensions getDimensions(Pose pPose) {
		if (box == null)
			return super.getDimensions(pPose);
		return new EntityDimensions(PackageItem.getWidth(box), PackageItem.getHeight(box), true);
	}

	public static PackageEntity spawn(SpawnEntity spawnEntity, Level world) {
		PackageEntity packageEntity =
			new PackageEntity(world, spawnEntity.getPosX(), spawnEntity.getPosY(), spawnEntity.getPosZ());
		packageEntity.setDeltaMovement(spawnEntity.getVelX(), spawnEntity.getVelY(), spawnEntity.getVelZ());
		packageEntity.clientPosition = packageEntity.position();
		return packageEntity;
	}

	public ItemStack getBox() {
		return box;
	}

	public static boolean centerPackage(Entity entity, Vec3 target) {
		if (!(entity instanceof PackageEntity packageEntity))
			return true;
		return packageEntity.decreaseInsertionTimer(target);
	}

	public boolean decreaseInsertionTimer(@Nullable Vec3 targetSpot) {
		if (targetSpot != null) {
			setDeltaMovement(getDeltaMovement().scale(.75f)
				.multiply(1, .25f, 1));
			Vec3 pos = position().add(targetSpot.subtract(position())
				.scale(.2f));
			setPos(pos.x, pos.y, pos.z);
			float yawTarget = ((int) getYRot()) / 90 * 90;
			setYRot(AngleHelper.angleLerp(.5f, getYRot(), yawTarget));
		}
		insertionDelay = Math.max(insertionDelay - 3, 0);
		return insertionDelay == 0;
	}

	public void setBox(ItemStack box) {
		this.box = box.copy();
		refreshDimensions();
	}

	@Override
	public boolean isPushable() {
		return true;
	}

	@Override
	public boolean canCollideWith(Entity pEntity) {
		return pEntity instanceof PackageEntity && pEntity.getBoundingBox().maxY < getBoundingBox().minY + .125f;
	}

	@Override
	public boolean canBeCollidedWith() {
		return false;
	}

	@Override
	public InteractionResult interact(Player pPlayer, InteractionHand pHand) {
		if (!pPlayer.getItemInHand(pHand)
			.isEmpty())
			return super.interact(pPlayer, pHand);
		if (pPlayer.level().isClientSide)
			return InteractionResult.SUCCESS;
		pPlayer.setItemInHand(pHand, box);
		level().playSound(null, blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, .2f,
			.75f + level().random.nextFloat());
		remove(RemovalReason.DISCARDED);
		return InteractionResult.SUCCESS;
	}

	@Override
	public void push(Entity entityIn) {
		boolean isOtherPackage = entityIn instanceof PackageEntity;

		if (!isOtherPackage && tossedBy.get() != null)
			tossedBy = new WeakReference<>(null); // no nudging

		if (isOtherPackage) {
			if (entityIn.getBoundingBox().minY < this.getBoundingBox().maxY)
				super.push(entityIn);
		} else if (entityIn.getBoundingBox().minY <= this.getBoundingBox().minY) {
			super.push(entityIn);
		}
	}

	@Override
	public double getPassengersRidingOffset() {
		return this.getDimensions(getPose()).height;
	}

	@Override
	protected void onInsideBlock(BlockState state) {
		super.onInsideBlock(state);
		if (!isAlive())
			return;
		if (state.getBlock() == Blocks.WATER) {
			destroy(damageSources().drown());
			remove(RemovalReason.KILLED);
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!ForgeHooks.onLivingAttack(this, source, amount))
			return false;

		if (level().isClientSide || !this.isAlive())
			return false;

		if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
			this.remove(RemovalReason.KILLED);
			return false;
		}

		if (!box.getItem().canBeHurtBy(source))
			return false;

		if (source.equals(damageSources().inWall()) && (isPassenger() || insertionDelay < 20))
			return false;

		if (source.is(DamageTypeTags.IS_FALL))
			return false;

		if (this.isInvulnerableTo(source))
			return false;

		if (source.is(DamageTypeTags.IS_EXPLOSION)) {
			this.destroy(source);
			this.remove(RemovalReason.KILLED);
			return false;
		}

		if (source.is(DamageTypeTags.IS_FIRE)) {
			if (this.isOnFire()) {
				this.takeDamage(source, 0.15F);
			} else {
				this.setSecondsOnFire(5);
			}
			return false;
		}

		boolean wasShot = source.getDirectEntity() instanceof AbstractArrow;
		boolean shotCanPierce = wasShot && ((AbstractArrow) source.getDirectEntity()).getPierceLevel() > 0;

		if (source.getEntity() instanceof Player && !((Player) source.getEntity()).getAbilities().mayBuild)
			return false;

		this.destroy(source);
		this.remove(RemovalReason.KILLED);
		return shotCanPierce;
	}

	private void takeDamage(DamageSource source, float amount) {
		float hp = this.getHealth();
		hp = hp - amount;
		if (hp <= 0.5F) {
			this.destroy(source);
			this.remove(RemovalReason.KILLED);
		} else {
			this.setHealth(hp);
		}
	}

	private void destroy(DamageSource source) {
		AllPackets.getChannel()
			.send(PacketDistributor.TRACKING_ENTITY.with(() -> this),
				new PackageDestroyPacket(getBoundingBox().getCenter(), box));
		ExtraSoundEvents.PACKAGE_POP.playOnServer(level(), blockPosition());
		this.dropAllDeathLoot(source);
	}

	@Override
	protected void dropAllDeathLoot(DamageSource pDamageSource) {
		super.dropAllDeathLoot(pDamageSource);
		ItemStackHandler contents = PackageItem.getContents(box);
		for (int i = 0; i < contents.getSlots(); i++) {
			ItemStack itemstack = contents.getStackInSlot(i);

			if (itemstack.getItem() instanceof SpawnEggItem sei && level() instanceof ServerLevel sl) {
				EntityType<?> entitytype = sei.getType(itemstack.getTag());
				Entity entity =
					entitytype.spawn(sl, itemstack, null, blockPosition(), MobSpawnType.SPAWN_EGG, false, false);
				if (entity != null)
					itemstack.shrink(1);
			}

			if (itemstack.isEmpty())
				continue;
			ItemEntity entityIn = new ItemEntity(level(), getX(), getY(), getZ(), itemstack);
			level().addFreshEntity(entityIn);
		}
	}

	@Override
	public void readAdditionalSaveData(CompoundTag compound) {
		super.readAdditionalSaveData(compound);
		box = ItemStack.of(compound.getCompound("Box"));
		refreshDimensions();
	}

	@Override
	public void addAdditionalSaveData(CompoundTag compound) {
		super.addAdditionalSaveData(compound);
		compound.put("Box", box.serializeNBT());
	}

	@Override
	public Packet<ClientGamePacketListener> getAddEntityPacket() {
		return NetworkHooks.getEntitySpawningPacket(this);
	}

	@Override
	public Iterable<ItemStack> getArmorSlots() {
		return Collections.emptyList();
	}

	@Override
	public ItemStack getItemBySlot(EquipmentSlot pSlot) {
		if (pSlot == EquipmentSlot.MAINHAND)
			return getBox();
		return ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(EquipmentSlot pSlot, ItemStack pStack) {
		if (pSlot == EquipmentSlot.MAINHAND)
			setBox(pStack);
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}

	@Override
	public InteractionHand getUsedItemHand() {
		return InteractionHand.MAIN_HAND;
	}

	@Override
	public void writeSpawnData(FriendlyByteBuf buffer) {
		buffer.writeItem(getBox());
		Vec3 motion = getDeltaMovement();
		buffer.writeFloat((float) motion.x);
		buffer.writeFloat((float) motion.y);
		buffer.writeFloat((float) motion.z);
	}

	@Override
	public void readSpawnData(FriendlyByteBuf additionalData) {
		setBox(additionalData.readItem());
		setDeltaMovement(additionalData.readFloat(), additionalData.readFloat(), additionalData.readFloat());
	}

	@Override
	public float getVoicePitch() {
		return 1.5f;
	}

	@Override
	public Fallsounds getFallSounds() {
		return new Fallsounds(SoundEvents.CHISELED_BOOKSHELF_FALL, SoundEvents.CHISELED_BOOKSHELF_FALL);
	}

	@Nullable
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return null;
	}

	@Nullable
	protected SoundEvent getDeathSound() {
		return null;
	}

	@Override
	public boolean isAffectedByPotions() {
		return false;
	}

	@Override
	public boolean fireImmune() {
		return box.getItem().isFireResistant() || super.fireImmune();
	}
}

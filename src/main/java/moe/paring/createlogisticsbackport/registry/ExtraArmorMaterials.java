package moe.paring.createlogisticsbackport.registry;

import com.google.common.base.Suppliers;
import com.simibubi.create.Create;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.function.Supplier;

public enum ExtraArmorMaterials implements ArmorMaterial {
    CARDBOARD(Create.asResource("cardboard")
            .toString(), 4, new int[] { 1, 1, 1, 1 }, 25, () -> SoundEvents.ARMOR_EQUIP_LEATHER, 0.0F, 0.0F,
            () -> Ingredient.of(ExtraItems.CARDBOARD))

    ;

    private static final int[] MAX_DAMAGE_ARRAY = new int[] { 11, 16, 15, 13 };
    private final String name;
    private final int maxDamageFactor;
    private final int[] damageReductionAmountArray;
    private final int enchantability;
    private final Supplier<SoundEvent> soundEvent;
    private final float toughness;
    private final float knockbackResistance;
    private final Supplier<Ingredient> repairMaterial;

    private ExtraArmorMaterials(String name, int maxDamageFactor, int[] damageReductionAmountArray, int enchantability,
                                Supplier<SoundEvent> soundEvent, float toughness, float knockbackResistance, Supplier<Ingredient> repairMaterial) {
        this.name = name;
        this.maxDamageFactor = maxDamageFactor;
        this.damageReductionAmountArray = damageReductionAmountArray;
        this.enchantability = enchantability;
        this.soundEvent = soundEvent;
        this.toughness = toughness;
        this.knockbackResistance = knockbackResistance;
        this.repairMaterial = Suppliers.memoize(repairMaterial::get);
    }

    @Override
    public int getEnchantmentValue() {
        return this.enchantability;
    }

    @Override
    public SoundEvent getEquipSound() {
        return this.soundEvent.get();
    }

    @Override
    public Ingredient getRepairIngredient() {
        return this.repairMaterial.get();
    }

    @Override
    public String getName() {
        return this.name;
    }

    @Override
    public float getToughness() {
        return this.toughness;
    }

    @Override
    public float getKnockbackResistance() {
        return this.knockbackResistance;
    }

    @Override
    public int getDurabilityForType(ArmorItem.Type pType) {
        return MAX_DAMAGE_ARRAY[pType.ordinal()] * this.maxDamageFactor;
    }

    @Override
    public int getDefenseForType(ArmorItem.Type pType) {
        return this.damageReductionAmountArray[pType.ordinal()];
    }

}


package slimeknights.tconstruct.library.tinkering;


import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;

import gnu.trove.set.hash.THashSet;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.translation.I18n;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import javax.annotation.Nonnull;

import slimeknights.mantle.util.RecipeMatch;
import slimeknights.tconstruct.common.config.Config;
import slimeknights.tconstruct.library.TinkerRegistry;
import slimeknights.tconstruct.library.Util;
import slimeknights.tconstruct.library.events.TinkerEvent;
import slimeknights.tconstruct.library.materials.HeadMaterialStats;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.modifiers.IModifier;
import slimeknights.tconstruct.library.modifiers.ModifierNBT;
import slimeknights.tconstruct.library.modifiers.TinkerGuiException;
import slimeknights.tconstruct.library.traits.ITrait;
import slimeknights.tconstruct.library.utils.TagUtil;
import slimeknights.tconstruct.library.utils.Tags;
import slimeknights.tconstruct.library.utils.TinkerUtil;
import slimeknights.tconstruct.library.utils.ToolBuilder;
import slimeknights.tconstruct.library.utils.ToolHelper;

/**
 * The base for each Tinker tool.
 */
public abstract class TinkersItem extends Item implements ITinkerable, IModifyable, IRepairable {

  protected final PartMaterialType[] requiredComponents;
  // used to classify what the thing can do
  protected final Set<Category> categories = new THashSet<Category>();

  public TinkersItem(PartMaterialType... requiredComponents) {
    this.requiredComponents = requiredComponents;

    this.setMaxStackSize(1);
    //this.setHasSubtypes(true);
  }

  /* Tool Information */
  public List<PartMaterialType> getRequiredComponents() {
    return ImmutableList.copyOf(requiredComponents);
  }

  protected void addCategory(Category... categories) {
    Collections.addAll(this.categories, categories);
  }

  public boolean hasCategory(Category category) {
    return categories.contains(category);
  }

  protected Category[] getCategories() {
    Category[] out = new Category[categories.size()];
    int i = 0;
    for(Category category : categories) {
      out[i++] = category;
    }

    return out;
  }

  /* INDESTRUCTIBLE */

  @Override
  public boolean hasCustomEntity(ItemStack stack) {
    return true;
  }

  @Nonnull
  @Override
  public Entity createEntity(World world, Entity location, ItemStack itemstack) {
    EntityItem entity = new IndestructibleEntityItem(world, location.posX, location.posY, location.posZ, itemstack);
    if(location instanceof EntityItem) {
      // workaround for private access on that field >_>
      NBTTagCompound tag = new NBTTagCompound();
      location.writeToNBT(tag);
      entity.setPickupDelay(tag.getShort("PickupDelay"));
    }
    entity.motionX = location.motionX;
    entity.motionY = location.motionY;
    entity.motionZ = location.motionZ;
    return entity;
  }

  /* Building the Item */
  public boolean validComponent(int slot, ItemStack stack) {
    if(slot > requiredComponents.length || slot < 0) {
      return false;
    }

    return requiredComponents[slot].isValid(stack);
  }


  /**
   * Builds an Itemstack of this tool with the given materials, if applicable.
   *
   * @param stacks Items to build with. Have to be in the correct order and have exact length. No nulls!
   * @return The built item or null if invalid input.
   */
  public ItemStack buildItemFromStacks(ItemStack[] stacks) {
    List<Material> materials = new ArrayList<Material>(stacks.length);

    if(stacks.length != requiredComponents.length) {
      return null;
    }

    // not a valid part arrangement for this tool
    for(int i = 0; i < stacks.length; i++) {
      if(!validComponent(i, stacks[i])) {
        return null;
      }

      materials.add(TinkerUtil.getMaterialFromStack(stacks[i]));
    }

    return buildItem(materials);
  }

  /**
   * Builds an Itemstack of this tool with the given materials.
   *
   * @param materials Materials to build with. Have to be in the correct order. No nulls!
   * @return The built item or null if invalid input.
   */
  public ItemStack buildItem(List<Material> materials) {
    ItemStack tool = new ItemStack(this);
    tool.setTagCompound(buildItemNBT(materials));

    return tool;
  }

  /**
   * Builds the NBT for a new tinker item with the given data.
   *
   * @param materials Materials to build with. Have to be in the correct order. No nulls!
   * @return The built nbt
   */
  public NBTTagCompound buildItemNBT(List<Material> materials) {
    NBTTagCompound basetag = new NBTTagCompound();
    NBTTagCompound toolTag = buildTag(materials);
    NBTTagCompound dataTag = buildData(materials);

    basetag.setTag(Tags.BASE_DATA, dataTag);
    basetag.setTag(Tags.TOOL_DATA, toolTag);
    // copy of the original tool data
    basetag.setTag(Tags.TOOL_DATA_ORIG, toolTag.copy());

    // save categories on the tool
    TagUtil.setCategories(basetag, getCategories());

    // add traits
    addMaterialTraits(basetag, materials);

    // fire toolbuilding event
    TinkerEvent.OnItemBuilding.fireEvent(basetag, ImmutableList.copyOf(materials));

    return basetag;
  }

  /**
   * Creates an NBT Tag with the materials that were used to build the item.
   */
  private NBTTagCompound buildData(List<Material> materials) {
    NBTTagCompound base = new NBTTagCompound();
    NBTTagList materialList = new NBTTagList();

    for(Material material : materials) {
      materialList.appendTag(new NBTTagString(material.identifier));
    }

    // pre-type base-modifier list
    NBTTagList modifierList = new NBTTagList();
    // we cannot set the type directly, but it gets typed by adding a tag, so we add and remove one
    modifierList.appendTag(new NBTTagString());
    modifierList.removeTag(0);

    base.setTag(Tags.BASE_MATERIALS, materialList);
    base.setTag(Tags.BASE_MODIFIERS, modifierList);

    return base;
  }

  /**
   * Builds an unusable tool that only has the rendering info
   */
  public ItemStack buildItemForRendering(List<Material> materials) {
    ItemStack tool = new ItemStack(this);
    NBTTagCompound base = new NBTTagCompound();
    base.setTag(Tags.BASE_DATA, buildData(materials));
    tool.setTagCompound(base);

    return tool;
  }

  public abstract NBTTagCompound buildTag(List<Material> materials);

  /** Checks whether an Item built from materials has only valid materials. Uses the standard NBT to determine materials. */
  public boolean hasValidMaterials(ItemStack stack) {
    // checks if the materials used support all stats needed
    NBTTagList list = TagUtil.getBaseMaterialsTagList(stack);
    List<Material> materials = TinkerUtil.getMaterialsFromTagList(list);

    // something went wrooooong
    if(materials.size() != requiredComponents.length) {
      return false;
    }

    // check if all materials used have the stats needed
    for(int i = 0; i < materials.size(); i++) {
      Material material = materials.get(i);
      PartMaterialType required = requiredComponents[i];
      if(!required.isValidMaterial(material)) {
        return false;
      }
    }

    return true;
  }

  public void addMaterialTraits(NBTTagCompound root, List<Material> materials) {
    int size = requiredComponents.length;
    // safety
    if(materials.size() < size) {
      size = materials.size();
    }
    // add corresponding traits per material usage
    for(int i = 0; i < size; i++) {
      PartMaterialType required = requiredComponents[i];
      Material material = materials.get(i);
      for(ITrait trait : required.getApplicableTraitsForMaterial(material)) {
        ToolBuilder.addTrait(root, trait, material.materialTextColor);
      }
    }
  }

  /* Repairing */

  /** Returns indices of the parts that are used for repairing */
  public int[] getRepairParts() {
    return new int[]{1}; // index 1 usually is the head. 0 is handle.
  }

  public float getRepairModifierForPart(int index) {
    return 1f;
  }

  @Override
  public ItemStack repair(ItemStack repairable, ItemStack[] repairItems) {
    if(repairable.getItemDamage() == 0 && !ToolHelper.isBroken(repairable)) {
      // undamaged and not broken - no need to repair
      return null;
    }

    // we assume the first required part exclusively determines repair material
    List<Material> materials = TinkerUtil.getMaterialsFromTagList(TagUtil.getBaseMaterialsTagList(repairable));
    if(materials.isEmpty()) {
      return null;
    }

    // ensure the items only contain valid items
    ItemStack[] items = Util.copyItemStackArray(repairItems);
    boolean foundMatch = false;
    for(int index : getRepairParts()) {
      Material material = materials.get(index);

      if(repairCustom(material, items) > 0) {
        foundMatch = true;
      }

      RecipeMatch.Match match = material.matches(items);

      // not a single match -> nothing to repair with
      if(match == null) {
        continue;
      }
      foundMatch = true;

      while((match = material.matches(items)) != null) {
        RecipeMatch.removeMatch(items, match);
      }
    }

    if(!foundMatch) {
      return null;
    }

    // check if all items were used
    for(int i = 0; i < repairItems.length; i++) {
      // was non-null and did not get modified (stacksize changed or null now, usually)
      if(repairItems[i] != null && ItemStack.areItemStacksEqual(repairItems[i], items[i])) {
        // found an item that was not touched
        return null;
      }
    }

    // now do it all over again with the real items, to actually repair \o/
    ItemStack item = repairable.copy();

    while(item.getItemDamage() > 0) {
      int amount = calculateRepairAmount(materials, repairItems);

      // nothing to repair with, we're therefore done
      if(amount <= 0) {
        break;
      }

      ToolHelper.repairTool(item, calculateRepair(item, amount));
      // save that we repaired it :I
      NBTTagCompound tag = TagUtil.getExtraTag(item);
      TagUtil.addInteger(tag, Tags.REPAIR_COUNT, 1);
      TagUtil.setExtraTag(item, tag);
    }

    return item;
  }

  /** Allows for custom repair items. Remove used items from the array. */
  protected int repairCustom(Material material, ItemStack[] repairItems) {
    return 0;
  }

  protected int calculateRepairAmount(List<Material> materials, ItemStack[] repairItems) {
    Set<Material> materialsMatched = Sets.newHashSet();
    float durability = 0f;
    // try to match each material once
    for(int index : getRepairParts()) {
      Material material = materials.get(index);

      if(materialsMatched.contains(material)) {
        continue;
      }

      // custom repairing
      durability += repairCustom(material, repairItems) * getRepairModifierForPart(index);

      RecipeMatch.Match match = material.matches(repairItems);
      if(match != null) {
        HeadMaterialStats stats = material.getStats(HeadMaterialStats.TYPE);
        if(stats != null) {
          materialsMatched.add(material);
          durability += ((float) stats.durability * (float) match.amount * getRepairModifierForPart(index)) / 144f;
          RecipeMatch.removeMatch(repairItems, match);
        }
      }
    }

    durability *= 1f + ((float) materialsMatched.size() - 1) / 9f;

    return (int) durability;
  }

  protected int calculateRepair(ItemStack tool, int amount) {
    float origDur = TagUtil.getOriginalToolStats(tool).durability;
    float actualDur = ToolHelper.getDurabilityStat(tool);

    // calculate in modifiers that change the total durability of a tool, like diamond
    // they should not punish the player with higher repair costs
    float durabilityFactor = actualDur / origDur;
    float increase = (float) amount * Math.min(10f, durabilityFactor);

    increase = Math.max(increase, actualDur / 64f);
    //increase = Math.max(50, increase);


    int modifiersUsed = TagUtil.getBaseModifiersUsed(tool.getTagCompound());
    float mods = 1.0f;
    if(modifiersUsed == 1) {
      mods = 0.95f;
    }
    else if(modifiersUsed == 2) {
      mods = 0.9f;
    }
    else if(modifiersUsed >= 3) {
      mods = 0.85f;
    }

    increase *= mods;

    NBTTagCompound tag = TagUtil.getExtraTag(tool);
    int repair = tag.getInteger(Tags.REPAIR_COUNT);
    float repairDimishingReturns = (100 - repair/2) / 100f;
    if(repairDimishingReturns < 0.5f) {
      repairDimishingReturns = 0.5f;
    }
    increase *= repairDimishingReturns;

    return (int) Math.ceil(increase);
  }

  /* Information */

  @Override
  public void addInformation(ItemStack stack, EntityPlayer playerIn, List<String> tooltip,
                             boolean advanced) {
    boolean shift = Util.isShiftKeyDown();
    boolean ctrl = Util.isCtrlKeyDown();
    // modifiers
    if(!shift && !ctrl) {
      getTooltip(stack, tooltip);

      tooltip.add("");
      // info tooltip for detailed and componend info
      tooltip.add(Util.translate("tooltip.tool.holdShift"));
      tooltip.add(Util.translate("tooltip.tool.holdCtrl"));

      tooltip.add(TextFormatting.BLUE +
                  I18n.translateToLocalFormatted("attribute.modifier.plus.0",
                                                 Util.df.format(ToolHelper.getActualDamage(stack, playerIn)),
                                                 I18n
                                                     .translateToLocal("attribute.name.generic.attackDamage")));
    }
    // detailed data
    else if(Config.extraTooltips && shift) {
      getTooltipDetailed(stack, tooltip);
    }
    // component data
    else if(Config.extraTooltips && ctrl) {
      getTooltipComponents(stack, tooltip);
    }
  }

  @Override
  public void getTooltip(ItemStack stack, List<String> tooltips) {
    // Default tooltip: modifiers
    NBTTagList tagList = TagUtil.getModifiersTagList(stack);
    for(int i = 0; i < tagList.tagCount(); i++) {
      NBTTagCompound tag = tagList.getCompoundTagAt(i);
      ModifierNBT data = ModifierNBT.readTag(tag);

      // get matching modifier
      IModifier modifier = TinkerRegistry.getModifier(data.identifier);
      if(modifier == null || modifier.isHidden()) {
        continue;
      }

      tooltips.add(data.getColorString() + modifier.getTooltip(tag, false));
    }
  }

  @Nonnull
  @Override
  public EnumRarity getRarity(ItemStack stack) {
    // prevents enchanted items to have a different name color
    return EnumRarity.COMMON;
  }

  @Override
  public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
    return false;
  }

  /* NBT loading */

  @Override
  public boolean updateItemStackNBT(NBTTagCompound nbt) {
    // when the itemstack is loaded from NBT we recalculate all the data
    if(nbt.hasKey(Tags.BASE_DATA)) {
      try {
        ToolBuilder.rebuildTool(nbt, this);
      } catch(TinkerGuiException e) {
        // nothing to do
      }
    }

    // return value shouldn't matter since it's never checked
    return true;
  }
}

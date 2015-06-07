package tconstruct.library.tools;

import net.minecraft.block.state.IBlockState;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.List;

import tconstruct.library.TinkerRegistry;
import tconstruct.library.tinkering.Category;
import tconstruct.library.tinkering.Material;
import tconstruct.library.tinkering.PartMaterialType;
import tconstruct.library.tinkering.TinkersItem;
import tconstruct.library.utils.TagUtil;
import tconstruct.library.utils.Tags;
import tconstruct.library.utils.ToolBuilder;
import tconstruct.library.utils.ToolTagUtil;
import tconstruct.library.utils.TooltipBuilder;
import tconstruct.tools.TinkerMaterials;

/**
 * Intermediate abstraction layer for all tools/melee weapons. This class has all the callbacks for blocks and enemies
 * so tools and weapons can share behaviour.
 */
public abstract class TinkersTool extends TinkersItem {

  public TinkersTool(PartMaterialType... requiredComponents) {
    super(requiredComponents);

    TinkerRegistry.addTool(this);
    addCategory(Category.TOOL);
  }

  @Override
  public float getDigSpeed(ItemStack itemstack, IBlockState state) {
    return ToolHelper.calcDigSpeed(itemstack, state);
  }

  @Override
  public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
    // deal damage
    return true;
  }

  @Override
  public String[] getInformation(ItemStack stack) {
    TooltipBuilder info = new TooltipBuilder(stack);

    info.addDurability();
    if(hasCategory(Category.HARVEST)) {
      info.addHarvestLevel();
      info.addMiningSpeed();
    }
    if(hasCategory(Category.WEAPON)) {
      info.addAttack();
    }

    return info.getTooltip();
  }

  @Override
  protected NBTTagCompound buildTag(Material[] materials) {
    // assumse a simple Head + Handle tool
    return ToolBuilder.buildSimpleTool(materials[0], materials[1]);
  }

  // Creative tab items


  @Override
  public void getSubItems(Item itemIn, CreativeTabs tab, List subItems) {
    for (Material head : TinkerRegistry.getAllMaterials()) {
      subItems.add(buildItem(new Material[]{head, TinkerMaterials.wood, TinkerMaterials.stone}));
    }
  }

  @Override
  public int getHarvestLevel(ItemStack stack, String toolClass) {
    if (this.getToolClasses(stack).contains(toolClass)) {
      NBTTagCompound tag = TagUtil.getToolTag(stack);
      if (tag != null) {
        return ToolTagUtil.getHarvestLevel(tag);
      }
    }
    return super.getHarvestLevel(stack, toolClass);
  }
}
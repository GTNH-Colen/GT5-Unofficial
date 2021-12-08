package gregtech.api.util;

import static gregtech.GT_Mod.GT_FML_LOGGER;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import cpw.mods.fml.common.FMLCommonHandler;
import gregtech.api.enums.GT_Values;
import gregtech.api.enums.ItemList;
import gregtech.api.objects.GT_ItemStack;
import gregtech.api.util.GT_Recipe.GT_Recipe_AssemblyLine;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nonnull;

public class GT_AssemblyLineUtils {

	/**
	 * A cache of Recipes using the Output as Key.
	 */
	private static HashMap<GT_ItemStack, GT_Recipe_AssemblyLine> sRecipeCacheByOutput = new HashMap<GT_ItemStack, GT_Recipe_AssemblyLine>();
	/**
	 * A cache of Recipes using the Recipe Hash String as Key.
	 */
	private static HashMap<String, GT_Recipe_AssemblyLine> sRecipeCacheByRecipeHash = new HashMap<String, GT_Recipe_AssemblyLine>();



	/**
	 * Checks the DataStick for deprecated/invalid recipes, updating them as required.
	 * @param aDataStick - The DataStick to process 
	 * @return Is this DataStick now valid with a current recipe?
	 */
	public static boolean processDataStick(ItemStack aDataStick) {
		if (!isItemDataStick(aDataStick)) {
			return false;
		}
		if (doesDataStickNeedUpdate(aDataStick)) {
			ItemStack aStickOutput = getDataStickOutput(aDataStick);
			if (aStickOutput != null) {
				GT_Recipe_AssemblyLine aIntendedRecipe = findAssemblyLineRecipeByOutput(aStickOutput);
				if (aIntendedRecipe != null) {
					return setAssemblyLineRecipeOnDataStick(aDataStick, aIntendedRecipe);
				}
			}
			return false;			
		}
		return true;
	}


	/**
	 * Finds an Assembly Line recipe from a DataStick.
	 * @param aDataStick - The DataStick to check.
	 * @return The GT_Recipe_AssemblyLine recipe contained on the DataStick, if any.
	 */
	public static GT_Recipe_AssemblyLine findAssemblyLineRecipeFromDataStick(ItemStack aDataStick) {	
		return findAssemblyLineRecipeFromDataStick(aDataStick, false).getRecipe();
	}

	/**
	 * Finds an Assembly Line recipe from a DataStick.
	 * @param aDataStick - The DataStick to check.
	 * @param aReturnBuiltRecipe - Do we return a GT_Recipe_AssemblyLine built from the data on the Data Stick instead of searching the Recipe Map?
	 * @return The GT_Recipe_AssemblyLine recipe contained on the DataStick, if any.
	 */
	@Nonnull
	public static LookupResult findAssemblyLineRecipeFromDataStick(ItemStack aDataStick, boolean aReturnBuiltRecipe) {
		if (!isItemDataStick(aDataStick)) {
			return LookupResultType.INVALID_STICK.getResult();
		}
		List<ItemStack> aInputs = new ArrayList<>(15);
		ItemStack aOutput = null;
		List<List<ItemStack>> mOreDictAlt = new ArrayList<>(15);
		List<FluidStack> aFluidInputs = new ArrayList<>(4);

		NBTTagCompound aTag = aDataStick.getTagCompound();
		if (aTag == null) {
			return LookupResultType.INVALID_STICK.getResult();
		}

		//Get From Cache
		if (doesDataStickHaveRecipeHash(aDataStick)) {
			GT_Recipe_AssemblyLine aRecipeFromCache = sRecipeCacheByRecipeHash.get(getHashFromDataStack(aDataStick));
			if (aRecipeFromCache != null) {
				return LookupResultType.VALID_STACK_AND_VALID_HASH.getResult(aRecipeFromCache);
			}
		}

		for (int i = 0; i < 15; i++) {
			int count = aTag.getInteger("a" + i);
			if (!aTag.hasKey("" + i) && count <= 0) {
				continue;
			}

			List<ItemStack> tAltCurrent = new ArrayList<>();
			for (int j = 0; j < count; j++) {
				ItemStack tLoaded = GT_Utility.loadItem(aTag, "a" + i + ":" + j);
				if (tLoaded == null) {
					continue;
				}
				tAltCurrent.add(tLoaded);
				if (GT_Values.D1) {
					GT_FML_LOGGER.info("Item Alt " + i + " : " + tLoaded.getUnlocalizedName());
				}
			}
			mOreDictAlt.add(tAltCurrent);
			ItemStack tLoaded = GT_Utility.loadItem(aTag, "" + i);
			if (tLoaded == null) {
				continue;
			}
			aInputs.add(tLoaded);
			if (GT_Values.D1) {
				GT_FML_LOGGER.info("Item " + i + " : " + tLoaded.getUnlocalizedName());
			}
		}

		if (GT_Values.D1) {
			GT_FML_LOGGER.info("All Items done, start fluid check");
		}
		for (int i = 0; i < 4; i++) {
			if (!aTag.hasKey("f" + i)) continue;
			FluidStack tLoaded = GT_Utility.loadFluid(aTag, "f" + i);
			if (tLoaded == null) continue;
			aFluidInputs.add(tLoaded);
			if (GT_Values.D1) {
				GT_FML_LOGGER.info("Fluid " + i + " " + tLoaded.getUnlocalizedName());
			}
		}
		aOutput = GT_Utility.loadItem(aTag, "output");
		if (!aTag.hasKey("output") || !aTag.hasKey("time") || aTag.getInteger("time") <= 0 || !aTag.hasKey("eu") || !GT_Utility.isStackValid(aOutput)) {
			return LookupResultType.INVALID_STICK.getResult();
		}
		if (GT_Values.D1) {
			GT_FML_LOGGER.info("Found Data Stick recipe");
		}
		
		int aTime = aTag.getInteger("time");
		int aEU = aTag.getInteger("eu");	

		// Try build a recipe instance
		if (aReturnBuiltRecipe) {
			return LookupResultType.VALID_STACK_AND_VALID_HASH.getResult(new GT_Recipe_AssemblyLine(null, 0, aInputs.toArray(new ItemStack[0]), aFluidInputs.toArray(new FluidStack[0]), aOutput, aTime, aEU));
		}


		for (GT_Recipe_AssemblyLine aRecipe : GT_Recipe.GT_Recipe_AssemblyLine.sAssemblylineRecipes) {
			if (aRecipe.mEUt != aEU || aRecipe.mDuration != aTime) continue;
			if (!GT_Utility.areStacksEqual(aOutput, aRecipe.mOutput, true)) continue;
			if (!GT_Utility.areStackListsEqual(Arrays.asList(aRecipe.mInputs), aInputs, false, true)) continue;
			if (!Objects.equals(Arrays.asList(aRecipe.mFluidInputs), aFluidInputs)) continue;
			if (!areStacksEqual(aRecipe.mOreDictAlt, mOreDictAlt)) continue;

			// Cache it
			String aRecipeHash = generateRecipeHash(aRecipe);
			sRecipeCacheByRecipeHash.put(aRecipeHash, aRecipe);
			sRecipeCacheByOutput.put(new GT_ItemStack(aRecipe.mOutput), aRecipe);
			return LookupResultType.VALID_STACK_AND_VALID_RECIPE.getResult(aRecipe);
		}
		return LookupResultType.VALID_STACK_BUT_INVALID_RECIPE.getResult();
	}

	private static boolean areStacksEqual(ItemStack[][] lhs, List<List<ItemStack>> rhs) {
		for (int i = 0; i < lhs.length; i++) {
			if (!areStacksEqual(lhs[i], rhs.get(i)))
				return false;
		}
		return true;
	}

	private static boolean areStacksEqual(ItemStack[] lhs, List<ItemStack> rhs) {
		return lhs == null ? rhs.isEmpty() : !rhs.isEmpty() && GT_Utility.areStackListsEqual(Arrays.asList(lhs), rhs, false, true);
	}


	/**
	 * Finds a GT_Recipe_AssemblyLine based on the expected output ItemStack.
	 * @param aOutput - The Output of a GT_Recipe_AssemblyLine.
	 * @return First found GT_Recipe_AssemblyLine with matching output.
	 */
	public static GT_Recipe_AssemblyLine findAssemblyLineRecipeByOutput(ItemStack aOutput) {
		if (aOutput == null) {
			return null;
		}	

		// Check the cache
		GT_ItemStack aCacheStack = new GT_ItemStack(aOutput);
		GT_Recipe_AssemblyLine aRecipeFromCache = sRecipeCacheByOutput.get(aCacheStack);
		if (aRecipeFromCache != null) {
			return aRecipeFromCache;
		}

		// Iterate all recipes and return the first matching based on Output.
		for (GT_Recipe_AssemblyLine aRecipe : GT_Recipe.GT_Recipe_AssemblyLine.sAssemblylineRecipes) {
			ItemStack aRecipeOutput = aRecipe.mOutput;
			if (GT_Utility.areStacksEqual(aRecipeOutput, aOutput)) {
				// Cache it to prevent future iterations of all recipes
				sRecipeCacheByOutput.put(aCacheStack, aRecipe);
				sRecipeCacheByRecipeHash.put(generateRecipeHash(aRecipe), aRecipe);
				return aRecipe;
			}
		}
		return null;
	}

	/**
	 * @param aRecipe - The recipe to generate a Recipe Hash String from.
	 * @return The Recipe Hash String.
	 */
	public static String generateRecipeHash(GT_Recipe_AssemblyLine aRecipe) {
		String aHash = "Invalid.Recipe.Hash";		
		if (aRecipe != null) {
			aHash = "Hash."+aRecipe.hashCode();
		}		
		return aHash;
	}

	/**
	 * @param aHash - Recipe hash String, may be null but will just be treated as invalid.
	 * @return Is this Recipe Hash String valid?
	 */
	public static boolean isValidHash(String aHash) {
		if (aHash != null && aHash.length() > 0) {
			if (!aHash.equals("Invalid.Recipe.Hash") && aHash.contains("Hash.")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @param aStack - The ItemStack to check.
	 * @return Is this ItemStack a Data Stick?
	 */
	public static boolean isItemDataStick(ItemStack aStack) {
		return GT_Utility.isStackValid(aStack) && ItemList.Tool_DataStick.isStackEqual(aStack, false, true);
	}

	/**
	 * @param aDataStick - The Data Stick to check.
	 * @return Does this Data Stick have a valid output ItemStack?
	 */
	public static boolean doesDataStickHaveOutput(ItemStack aDataStick) {
		if (isItemDataStick(aDataStick) && aDataStick.hasTagCompound() && aDataStick.getTagCompound().hasKey("output")) {
			return true;
		}
		return false;
	}

	/**
	 * @param aDataStick - The Data Stick to check.
	 * @return Does this Data Stick need recipe data updated.
	 */
	public static boolean doesDataStickNeedUpdate(ItemStack aDataStick) {
		if (isItemDataStick(aDataStick) && doesDataStickHaveRecipeHash(aDataStick)) {
			String aStickHash = getHashFromDataStack(aDataStick);
			if (isValidHash(aStickHash) && doesDataStickHaveOutput(aDataStick)) {
				ItemStack aStickOutput = getDataStickOutput(aDataStick);
				GT_Recipe_AssemblyLine aIntendedRecipe = findAssemblyLineRecipeByOutput(aStickOutput);
				if (aStickHash.equals(generateRecipeHash(aIntendedRecipe))) {
					return false;
				}				
			}
		}	
		return true;
	}

	/**
	 * @param aDataStick - The Data Stick to check.
	 * @return Does this have a Recipe Hash String at all?
	 */
	public static boolean doesDataStickHaveRecipeHash(ItemStack aDataStick) {
		if (isItemDataStick(aDataStick) && aDataStick.hasTagCompound()) {
			NBTTagCompound aNBT = aDataStick.getTagCompound();
			if (aNBT.hasKey("Data.Recipe.Hash")) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Get the Output ItemStack from a Data Stick.
	 * @param aDataStick - The Data Stick to check.
	 * @return Output ItemStack contained on the Data Stick.
	 */
	public static ItemStack getDataStickOutput(ItemStack aDataStick) {
		if (doesDataStickHaveOutput(aDataStick)) {
			ItemStack aOutput = GT_Utility.loadItem(aDataStick.getTagCompound(), "output");
			return aOutput;
		}
		return null;
	}

	/**
	 * @param aDataStick - The Data Stick to procces.
	 * @return The stored Recipe Hash String on the Data Stick, will return an invalid Hash if one is not found. <p>
	 * Check with isValidHash(). <p> 
	 * Will not return Null.
	 */
	public static String getHashFromDataStack(ItemStack aDataStick) {
		if (isItemDataStick(aDataStick) && aDataStick.hasTagCompound()) {
			NBTTagCompound aNBT = aDataStick.getTagCompound();
			if (aNBT.hasKey("Data.Recipe.Hash")) {
				return aNBT.getString("Data.Recipe.Hash");

			}
		}
		return "Invalid.Recipe.Hash";
	}

	/**
	 * 
	 * @param aDataStick - The Data Stick to update.
	 * @param aRecipeHash - The Recipe Hash String to update with.
	 * @return Did we update the Recipe Hash String on the Data Stick?
	 */
	public static boolean setRecipeHashOnDataStick(ItemStack aDataStick, String aRecipeHash) {
		if (isItemDataStick(aDataStick) && aDataStick.hasTagCompound()) {
			NBTTagCompound aNBT = aDataStick.getTagCompound();
			aNBT.setString("Data.Recipe.Hash", aRecipeHash);
			aDataStick.setTagCompound(aNBT);
			return true;
		}
		return false;
	}

	/**
	 * 
	 * @param aDataStick - The Data Stick to update.
	 * @param aNewRecipe - The New GT_Recipe_AssemblyLine recipe to update it with.
	 * @return Did we set the new recipe data & Recipe Hash String on the Data Stick?
	 */
	public static boolean setAssemblyLineRecipeOnDataStick(ItemStack aDataStick, GT_Recipe_AssemblyLine aNewRecipe) {
		if (isItemDataStick(aDataStick)) {
			String s = aNewRecipe.mOutput.getDisplayName();
			if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
				s = GT_Assemblyline_Server.lServerNames.get(aNewRecipe.mOutput.getDisplayName());
				if (s == null) {
					s = aNewRecipe.mOutput.getDisplayName();
				}
			}

			String aHash = generateRecipeHash(aNewRecipe);
			if (GT_Values.D1) {
				GT_Recipe_AssemblyLine aOldRecipe = findAssemblyLineRecipeFromDataStick(aDataStick, true).recipe;
				GT_FML_LOGGER.info("Updating data stick: "+aDataStick.getDisplayName()+" | Old Recipe Hash: "+generateRecipeHash(aOldRecipe)+", New Recipe Hash: "+aHash);
			}


			//remove possible old NBTTagCompound
			aDataStick.setTagCompound(new NBTTagCompound());
			if (GT_Values.D1) {
				GT_Utility.ItemNBT.setBookTitle(aDataStick, s + " Construction Data ("+aHash+")");				
			}
			else {
				GT_Utility.ItemNBT.setBookTitle(aDataStick, s + " Construction Data");				
			}

			NBTTagCompound tNBT = aDataStick.getTagCompound();
			if (tNBT == null) {
				tNBT = new NBTTagCompound();
			}

			tNBT.setTag("output", aNewRecipe.mOutput.writeToNBT(new NBTTagCompound()));
			tNBT.setInteger("time", aNewRecipe.mDuration);
			tNBT.setInteger("eu", aNewRecipe.mEUt);
			for (int i = 0; i < aNewRecipe.mInputs.length; i++) {
				tNBT.setTag("" + i, aNewRecipe.mInputs[i].writeToNBT(new NBTTagCompound()));
			}
			for (int i = 0; i < aNewRecipe.mOreDictAlt.length; i++) {
				if (aNewRecipe.mOreDictAlt[i] != null && aNewRecipe.mOreDictAlt[i].length > 0) {
					tNBT.setInteger("a" + i, aNewRecipe.mOreDictAlt[i].length);
					for (int j = 0; j < aNewRecipe.mOreDictAlt[i].length; j++) {
						tNBT.setTag("a" + i + ":" + j, aNewRecipe.mOreDictAlt[i][j].writeToNBT(new NBTTagCompound()));
					}
				}
			}
			for (int i = 0; i < aNewRecipe.mFluidInputs.length; i++) {
				tNBT.setTag("f" + i, aNewRecipe.mFluidInputs[i].writeToNBT(new NBTTagCompound()));
			}
			tNBT.setString("author", "Assembling Line Recipe Generator");
			NBTTagList tNBTList = new NBTTagList();
			s = aNewRecipe.mOutput.getDisplayName();
			if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
				s = GT_Assemblyline_Server.lServerNames.get(aNewRecipe.mOutput.getDisplayName());
				if (s == null)
					s = aNewRecipe.mOutput.getDisplayName();
			}
			tNBTList.appendTag(new NBTTagString("Construction plan for " + aNewRecipe.mOutput.stackSize + " " + s + ". Needed EU/t: " + aNewRecipe.mEUt + " Production time: " + (aNewRecipe.mDuration / 20)));
			for (int i = 0; i < aNewRecipe.mInputs.length; i++) {
				if (aNewRecipe.mOreDictAlt[i] != null) {
					int count = 0;
					StringBuilder tBuilder = new StringBuilder("Input Bus " + (i + 1) + ": ");
					for (ItemStack tStack : aNewRecipe.mOreDictAlt[i]) {
						if (tStack != null) {
							s = tStack.getDisplayName();
							if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
								s = GT_Assemblyline_Server.lServerNames.get(tStack.getDisplayName());
								if (s == null)
									s = tStack.getDisplayName();
							}


							tBuilder.append(count == 0 ? "" : "\nOr ").append(tStack.stackSize).append(" ").append(s);
							count++;
						}
					}
					if (count > 0) tNBTList.appendTag(new NBTTagString(tBuilder.toString()));
				} else if (aNewRecipe.mInputs[i] != null) {
					s = aNewRecipe.mInputs[i].getDisplayName();
					if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
						s = GT_Assemblyline_Server.lServerNames.get(aNewRecipe.mInputs[i].getDisplayName());
						if (s == null)
							s = aNewRecipe.mInputs[i].getDisplayName();
					}
					tNBTList.appendTag(new NBTTagString("Input Bus " + (i + 1) + ": " + aNewRecipe.mInputs[i].stackSize + " " + s));
				}
			}
			for (int i = 0; i < aNewRecipe.mFluidInputs.length; i++) {
				if (aNewRecipe.mFluidInputs[i] != null) {
					s = aNewRecipe.mFluidInputs[i].getLocalizedName();
					if (FMLCommonHandler.instance().getEffectiveSide().isServer()) {
						s = GT_Assemblyline_Server.lServerNames.get(aNewRecipe.mFluidInputs[i].getLocalizedName());
						if (s == null)
							s = aNewRecipe.mFluidInputs[i].getLocalizedName();
					}
					tNBTList.appendTag(new NBTTagString("Input Hatch " + (i + 1) + ": " + aNewRecipe.mFluidInputs[i].amount + "L " + s));
				}
			}
			tNBT.setTag("pages", tNBTList);		
			aDataStick.setTagCompound(tNBT);	
			// Set recipe hash
			setRecipeHashOnDataStick(aDataStick, aHash);
			return true;
		}
		return false;
	}

	public enum LookupResultType {
		INVALID_STICK(false),
		VALID_STACK_BUT_INVALID_RECIPE(false),
		VALID_STACK_AND_VALID_RECIPE(true),
		VALID_STACK_AND_VALID_HASH(true);

		private final boolean recipeNull;
		private LookupResult singletonResult;

		LookupResultType(boolean recipeNull) {
			this.recipeNull = recipeNull;
		}

		public LookupResult getResult() {
			if (!recipeNull)
				throw new IllegalArgumentException("This result type require a nonnull recipe");
			if (singletonResult == null)
				singletonResult = new LookupResult(null, this);
			return singletonResult;
		}

		public LookupResult getResult(GT_Recipe_AssemblyLine recipe) {
			if ((recipe == null) == recipeNull)
				throw new IllegalArgumentException("This result type does not allow given input");
			return new LookupResult(recipe, this);
		}
	}

	public static class LookupResult {
		private final GT_Recipe_AssemblyLine recipe;
		private final LookupResultType type;

		LookupResult(GT_Recipe_AssemblyLine recipe, LookupResultType type) {
			this.recipe = recipe;
			this.type = type;
		}

		public GT_Recipe_AssemblyLine getRecipe() {
			return recipe;
		}

		public LookupResultType getType() {
			return type;
		}
	}
}

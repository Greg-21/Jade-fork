package snownee.jade.api.ui;

import java.util.function.Function;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.JadeInternals;
import snownee.jade.api.ITooltip;
import snownee.jade.api.fluid.JadeFluidObject;

public interface IElementHelper {

	static IElementHelper get() {
		return JadeInternals.getElementHelper();
	}

	ITextElement text(Component component);

	IElement spacer(int x, int y);

	IElement item(ItemStack itemStack);

	IElement item(ItemStack itemStack, float scale);

	IElement item(ItemStack itemStack, float scale, @Nullable String text);

	IElement smallItem(ItemStack itemStack);

	IElement fluid(JadeFluidObject fluid);

	IElement progress(float progress, @Nullable Component text, ProgressStyle style, BoxStyle boxStyle, boolean canDecrease);

	IElement progress(float progress);

	IElement progress(
			float progress,
			ResourceLocation baseSprite,
			ResourceLocation progressSprite,
			int width,
			int height,
			boolean canDecrease);

	/**
	 * Display a nested tooltip
	 */
	IBoxElement box(ITooltip tooltip, BoxStyle boxStyle);

	/**
	 * Create an empty tooltip. Used by the {@code box} method.
	 */
	ITooltip tooltip();

	ProgressStyle progressStyle();

	IElement sprite(Function<ResourceLocation, RenderType> function, ResourceLocation sprite, int width, int height);

	IElement sprite(ResourceLocation sprite, int width, int height);
}

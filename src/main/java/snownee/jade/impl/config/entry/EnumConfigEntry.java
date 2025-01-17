package snownee.jade.impl.config.entry;

import java.util.function.BiConsumer;

import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.gui.config.OptionsList;
import snownee.jade.gui.config.value.OptionValue;

public class EnumConfigEntry<E extends Enum<E>> extends ConfigEntry<E> {

	public EnumConfigEntry(ResourceLocation id, E defaultValue) {
		super(id, defaultValue);
	}

	@Override
	public boolean isValidValue(Object value) {
		return value.getClass() == defaultValue().getClass();
	}

	@Override
	public E convertValue(Object value) {
		if (value.getClass() == String.class) {
			//noinspection unchecked
			return (E) Enum.valueOf(defaultValue().getClass(), (String) value);
		}
		//noinspection unchecked
		return (E) value;
	}

	@Override
	public OptionValue<?> createUI(
			OptionsList options,
			String optionName,
			IPluginConfig config,
			BiConsumer<ResourceLocation, Object> setter) {
		//noinspection unchecked
		return options.choices(optionName, () -> (E) config.getEnum(id), e -> setter.accept(id, e));
	}

}

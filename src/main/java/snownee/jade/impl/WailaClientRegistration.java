package snownee.jade.impl;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.mojang.datafixers.util.Pair;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.Jade;
import snownee.jade.api.Accessor;
import snownee.jade.api.AccessorClientHandler;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IComponentProvider;
import snownee.jade.api.IToggleableProvider;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.JadeIds;
import snownee.jade.api.callback.JadeAfterRenderCallback;
import snownee.jade.api.callback.JadeBeforeRenderCallback;
import snownee.jade.api.callback.JadeBeforeTooltipCollectCallback;
import snownee.jade.api.callback.JadeItemModNameCallback;
import snownee.jade.api.callback.JadeRayTraceCallback;
import snownee.jade.api.callback.JadeTooltipCollectedCallback;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.api.config.IgnoreList;
import snownee.jade.api.platform.CustomEnchantPower;
import snownee.jade.api.view.EnergyView;
import snownee.jade.api.view.FluidView;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.ItemView;
import snownee.jade.api.view.ProgressView;
import snownee.jade.gui.PluginsConfigScreen;
import snownee.jade.gui.config.OptionsList;
import snownee.jade.impl.config.entry.BooleanConfigEntry;
import snownee.jade.impl.config.entry.ConfigEntry;
import snownee.jade.impl.config.entry.EnumConfigEntry;
import snownee.jade.impl.config.entry.FloatConfigEntry;
import snownee.jade.impl.config.entry.IntConfigEntry;
import snownee.jade.impl.config.entry.StringConfigEntry;
import snownee.jade.impl.lookup.HierarchyLookup;
import snownee.jade.overlay.DatapackBlockManager;
import snownee.jade.util.ClientProxy;
import snownee.jade.util.JadeCodecs;
import snownee.jade.util.JsonConfig;
import snownee.jade.util.ModIdentification;

public class WailaClientRegistration implements IWailaClientRegistration {

	private static final WailaClientRegistration INSTANCE = new WailaClientRegistration();

	public final HierarchyLookup<IComponentProvider<BlockAccessor>> blockIconProviders;
	public final HierarchyLookup<IComponentProvider<BlockAccessor>> blockComponentProviders;

	public final HierarchyLookup<IComponentProvider<EntityAccessor>> entityIconProviders;
	public final HierarchyLookup<IComponentProvider<EntityAccessor>> entityComponentProviders;

	public final Set<Block> hideBlocks = Sets.newHashSet();
	private ImmutableSet<Block> hideBlocksReloadable = ImmutableSet.of();
	public final Set<EntityType<?>> hideEntities = Sets.newHashSet();
	private ImmutableSet<EntityType<?>> hideEntitiesReloadable = ImmutableSet.of();
	public final Set<Block> pickBlocks = Sets.newHashSet();
	public final Set<EntityType<?>> pickEntities = Sets.newHashSet();

	public final CallbackContainer<JadeAfterRenderCallback> afterRenderCallback = new CallbackContainer<>();
	public final CallbackContainer<JadeBeforeRenderCallback> beforeRenderCallback = new CallbackContainer<>();
	public final CallbackContainer<JadeRayTraceCallback> rayTraceCallback = new CallbackContainer<>();
	public final CallbackContainer<JadeTooltipCollectedCallback> tooltipCollectedCallback = new CallbackContainer<>();
	public final CallbackContainer<JadeItemModNameCallback> itemModNameCallback = new CallbackContainer<>();
	public final CallbackContainer<JadeBeforeTooltipCollectCallback> beforeTooltipCollectCallback = new CallbackContainer<>();

	public final Map<ResourceLocation, ConfigEntry<?>> configEntries = Maps.newHashMap();
	public final List<Pair<ResourceLocation, Consumer<ResourceLocation>>> configListeners = Lists.newArrayList();
	public final Multimap<ResourceLocation, Component> configCategoryOverrides = ArrayListMultimap.create();

	public final Map<Block, CustomEnchantPower> customEnchantPowers = Maps.newHashMap();
	public final Map<ResourceLocation, IClientExtensionProvider<ItemStack, ItemView>> itemStorageProviders = Maps.newHashMap();
	public final Map<ResourceLocation, IClientExtensionProvider<CompoundTag, FluidView>> fluidStorageProviders = Maps.newHashMap();
	public final Map<ResourceLocation, IClientExtensionProvider<CompoundTag, EnergyView>> energyStorageProviders = Maps.newHashMap();
	public final Map<ResourceLocation, IClientExtensionProvider<CompoundTag, ProgressView>> progressProviders = Maps.newHashMap();

	public final Set<ResourceLocation> clientFeatures = Sets.newHashSet();

	public final Map<Class<Accessor<?>>, AccessorClientHandler<Accessor<?>>> accessorHandlers = Maps.newIdentityHashMap();
	private ClientRegistrationSession session;

	WailaClientRegistration() {
		blockIconProviders = new HierarchyLookup<>(Block.class);
		blockComponentProviders = new HierarchyLookup<>(Block.class);

		entityIconProviders = new HierarchyLookup<>(Entity.class);
		entityComponentProviders = new HierarchyLookup<>(Entity.class);
	}

	public static WailaClientRegistration instance() {
		return INSTANCE;
	}

	public static <T> JsonConfig<IgnoreList<T>> createIgnoreListConfig(
			String file,
			ResourceKey<Registry<T>> registryKey,
			List<String> defaultValues) {
		List<String> values = List.copyOf(defaultValues);
		return new JsonConfig<>(Jade.ID + "/" + file, JadeCodecs.ignoreList(registryKey), null, () -> {
			var ignoreList = new IgnoreList<T>();
			ignoreList.values = values;
			return ignoreList;
		});
	}

	@Override
	public void registerBlockIcon(IComponentProvider<BlockAccessor> provider, Class<? extends Block> blockClass) {
		if (isSessionActive()) {
			session.registerBlockIcon(provider, blockClass);
		} else {
			blockIconProviders.register(blockClass, provider);
			tryAddConfig(provider);
		}
	}

	@Override
	public void registerBlockComponent(IComponentProvider<BlockAccessor> provider, Class<? extends Block> blockClass) {
		if (isSessionActive()) {
			session.registerBlockComponent(provider, blockClass);
		} else {
			blockComponentProviders.register(blockClass, provider);
			tryAddConfig(provider);
		}
	}

	@Override
	public void registerEntityIcon(IComponentProvider<EntityAccessor> provider, Class<? extends Entity> entityClass) {
		if (isSessionActive()) {
			session.registerEntityIcon(provider, entityClass);
		} else {
			entityIconProviders.register(entityClass, provider);
			tryAddConfig(provider);
		}
	}

	@Override
	public void registerEntityComponent(IComponentProvider<EntityAccessor> provider, Class<? extends Entity> entityClass) {
		if (isSessionActive()) {
			session.registerEntityComponent(provider, entityClass);
		} else {
			entityComponentProviders.register(entityClass, provider);
			tryAddConfig(provider);
		}
	}

	public List<IComponentProvider<BlockAccessor>> getBlockProviders(
			Block block,
			Predicate<IComponentProvider<? extends Accessor<?>>> filter) {
		return blockComponentProviders.get(block).stream().filter(filter).toList();
	}

	public List<IComponentProvider<BlockAccessor>> getBlockIconProviders(
			Block block,
			Predicate<IComponentProvider<? extends Accessor<?>>> filter) {
		return blockIconProviders.get(block).stream().filter(filter).toList();
	}

	public List<IComponentProvider<EntityAccessor>> getEntityProviders(
			Entity entity, Predicate<IComponentProvider<? extends Accessor<?>>> filter) {
		return entityComponentProviders.get(entity).stream().filter(filter).toList();
	}

	public List<IComponentProvider<EntityAccessor>> getEntityIconProviders(
			Entity entity, Predicate<IComponentProvider<? extends Accessor<?>>> filter) {
		return entityIconProviders.get(entity).stream().filter(filter).toList();
	}

	@Override
	public void hideTarget(Block block) {
		Objects.requireNonNull(block);
		hideBlocks.add(block);
	}

	@Override
	public void hideTarget(EntityType<?> entityType) {
		Objects.requireNonNull(entityType);
		hideEntities.add(entityType);
	}

	@Override
	public void usePickedResult(Block block) {
		Objects.requireNonNull(block);
		pickBlocks.add(block);
	}

	@Override
	public void usePickedResult(EntityType<?> entityType) {
		Objects.requireNonNull(entityType);
		pickEntities.add(entityType);
	}

	@Override
	public boolean shouldHide(BlockState state) {
		return state.isAir() || hideBlocksReloadable.contains(state.getBlock());
	}

	@Override
	public boolean shouldPick(BlockState state) {
		return pickBlocks.contains(state.getBlock());
	}

	@Override
	public boolean shouldHide(Entity entity) {
		return hideEntitiesReloadable.contains(entity.getType());
	}

	@Override
	public boolean shouldPick(Entity entity) {
		return pickEntities.contains(entity.getType());
	}

	public void addConfig(ConfigEntry<?> entry) {
		Objects.requireNonNull(entry);
		Preconditions.checkArgument(StringUtils.countMatches(entry.id().getPath(), '.') <= 1);
		Preconditions.checkArgument(!hasConfig(entry.id()), "Duplicate config key: %s", entry.id());
		Preconditions.checkArgument(
				entry.isValidValue(entry.defaultValue()),
				"Default value of config %s does not pass value check",
				entry.id());
		configEntries.put(entry.id(), entry);
	}

	@Override
	public void addConfig(ResourceLocation key, boolean defaultValue) {
		if (isSessionActive()) {
			session.addConfig(key, defaultValue);
		} else {
			addConfig(new BooleanConfigEntry(key, defaultValue));
		}
	}

	@Override
	public <T extends Enum<T>> void addConfig(ResourceLocation key, T defaultValue) {
		Objects.requireNonNull(defaultValue);
		if (isSessionActive()) {
			session.addConfig(key, defaultValue);
		} else {
			addConfig(new EnumConfigEntry<>(key, defaultValue));
		}
	}

	@Override
	public void addConfig(ResourceLocation key, String defaultValue, Predicate<String> validator) {
		Objects.requireNonNull(defaultValue);
		Objects.requireNonNull(validator);
		if (isSessionActive()) {
			session.addConfig(key, defaultValue, validator);
		} else {
			addConfig(new StringConfigEntry(key, defaultValue, validator));
		}
	}

	@Override
	public void addConfig(ResourceLocation key, int defaultValue, int min, int max, boolean slider) {
		if (isSessionActive()) {
			session.addConfig(key, defaultValue, min, max, slider);
		} else {
			addConfig(new IntConfigEntry(key, defaultValue, min, max, slider));
		}
	}

	@Override
	public void addConfig(ResourceLocation key, float defaultValue, float min, float max, boolean slider) {
		if (isSessionActive()) {
			session.addConfig(key, defaultValue, min, max, slider);
		} else {
			addConfig(new FloatConfigEntry(key, defaultValue, min, max, slider));
		}
	}

	@Override
	public void addConfigListener(ResourceLocation key, Consumer<ResourceLocation> listener) {
		Objects.requireNonNull(listener);
		if (isSessionActive()) {
			session.addConfigListener(key, listener);
		} else {
			Preconditions.checkArgument(hasConfig(key), "Unknown config key: %s", key);
			Objects.requireNonNull(getConfigEntry(key)).addListener(listener);
		}
	}

	@Override
	public void setConfigCategoryOverride(ResourceLocation key, Component override) {
		Preconditions.checkArgument(!JadeIds.isAccess(key), "Cannot override option from access category");
		Preconditions.checkArgument(IPluginConfig.isPrimaryKey(key), "Only primary config key can be overridden");
		if (isSessionActive()) {
			session.setConfigCategoryOverride(key, override);
		} else {
			Preconditions.checkArgument(hasConfig(key), "Unknown config key: %s", key);
			configCategoryOverrides.put(key, override);
		}
	}

	@Override
	public void setConfigCategoryOverride(ResourceLocation key, List<Component> overrides) {
		for (Component override : overrides) {
			setConfigCategoryOverride(key, override);
		}
	}

	private void tryAddConfig(IToggleableProvider provider) {
		if (!provider.isRequired() && !hasConfig(provider.getUid())) {
			addConfig(provider.getUid(), provider.enabledByDefault());
		}
	}

	@Override
	public Set<ResourceLocation> getConfigKeys(String namespace) {
		return getConfigKeys().stream().filter(id -> id.getNamespace().equals(namespace)).collect(Collectors.toSet());
	}

	@Override
	public Set<ResourceLocation> getConfigKeys() {
		return configEntries.keySet();
	}

	@Override
	public boolean hasConfig(ResourceLocation key) {
		return getConfigKeys().contains(key);
	}

	@Nullable
	public ConfigEntry<?> getConfigEntry(ResourceLocation key) {
		return configEntries.get(key);
	}

	public List<Category> getConfigListView(boolean enableAccessibilityPlugins) {
		Multimap<String, ConfigEntry<?>> categoryMap = ArrayListMultimap.create();
		configCategoryOverrides.forEach((key, component) -> {
			categoryMap.put(component.getString(), getConfigEntry(key));
		});
		configEntries.forEach((key, entry) -> {
			if (configCategoryOverrides.containsKey(key)) {
				return;
			}
			if (!enableAccessibilityPlugins && JadeIds.isAccess(key)) {
				return;
			}
			if (!IPluginConfig.isPrimaryKey(key)) {
				ResourceLocation primaryKey = IPluginConfig.getPrimaryKey(key);
				Collection<Component> components = configCategoryOverrides.get(primaryKey);
				if (!components.isEmpty()) {
					for (Component component : components) {
						categoryMap.put(component.getString(), entry);
					}
					return;
				}
			}
			String namespace = key.getNamespace();
			Optional<String> modName = ModIdentification.getModName(namespace);
			if (!Jade.ID.equals(namespace) && modName.isPresent()) {
				categoryMap.put(modName.get(), entry);
			} else {
				categoryMap.put(I18n.get(OptionsList.Entry.makeKey("plugin_" + namespace)), entry);
			}
		});

		return categoryMap.asMap().entrySet().stream()
				.map(e -> new Category(Component.literal(e.getKey()), e.getValue().stream()
						.sorted(Comparator.comparingInt($ -> WailaCommonRegistration.instance().priorities.getSortedList()
								.indexOf($.id())))
						.toList()
				))
				.sorted(Comparator.comparingInt(specialOrder()).thenComparing($ -> $.title().getString()))
				.toList();
	}

	private static ToIntFunction<Category> specialOrder() {
		String core = I18n.get(OptionsList.Entry.makeKey("plugin_" + Jade.ID));
		String debug = I18n.get(OptionsList.Entry.makeKey("plugin_" + Jade.ID + ".debug"));
		// core is always the first, debug is always the last
		return category -> {
			String title = category.title().getString();
			if (core.equals(title)) {
				return -1;
			}
			if (debug.equals(title)) {
				return 1;
			}
			return 0;
		};
	}

	public void setServerConfig(Map<ResourceLocation, Object> config) {
		for (ConfigEntry<?> entry : configEntries.values()) {
			entry.setSyncedValue(null);
		}
		config.forEach((key, value) -> {
			//noinspection rawtypes
			ConfigEntry entry = getConfigEntry(key);
			if (entry != null) {
				try {
					value = entry.convertValue(value);
					if (entry.isValidValue(value)) {
						//noinspection unchecked
						entry.setSyncedValue(value);
					}
				} catch (Exception ignored) {
				}
			}
		});
		Jade.config().fixData();
	}

	public record Category(MutableComponent title, List<ConfigEntry<?>> entries) {
	}

	public void loadComplete() {
		reloadIgnoreLists();
		var priorities = WailaCommonRegistration.instance().priorities;
		blockComponentProviders.loadComplete(priorities);
		blockIconProviders.loadComplete(priorities);
		entityComponentProviders.loadComplete(priorities);
		entityIconProviders.loadComplete(priorities);
		Stream.of(
				afterRenderCallback,
				beforeRenderCallback,
				rayTraceCallback,
				tooltipCollectedCallback,
				itemModNameCallback,
				beforeTooltipCollectCallback).forEach(CallbackContainer::sort);
		session = null;
	}

	public synchronized void reloadIgnoreLists() {
		{
			ImmutableSet.Builder<EntityType<?>> builder = ImmutableSet.builder();
			builder.addAll(hideEntities);
			createIgnoreListConfig(
					"hide-entities",
					Registries.ENTITY_TYPE,
					List.of("area_effect_cloud", "firework_rocket", "interaction", "text_display", "lightning_bolt")).get().reload(
					BuiltInRegistries.ENTITY_TYPE,
					builder::add);
			hideEntitiesReloadable = builder.build();
		}
		{
			ImmutableSet.Builder<Block> builder = ImmutableSet.builder();
			builder.addAll(hideBlocks);
			createIgnoreListConfig(
					"hide-blocks",
					Registries.BLOCK,
					List.of("barrier")).get().reload(
					BuiltInRegistries.BLOCK,
					builder::add);
			hideBlocksReloadable = builder.build();
		}
	}

	@Override
	public void addAfterRenderCallback(int priority, JadeAfterRenderCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addAfterRenderCallback(priority, callback);
		} else {
			afterRenderCallback.add(priority, callback);
		}
	}

	@Override
	public void addBeforeRenderCallback(int priority, JadeBeforeRenderCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addBeforeRenderCallback(priority, callback);
		} else {
			beforeRenderCallback.add(priority, callback);
		}
	}

	@Override
	public void addRayTraceCallback(int priority, JadeRayTraceCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addRayTraceCallback(priority, callback);
		} else {
			rayTraceCallback.add(priority, callback);
		}
	}

	@Override
	public void addTooltipCollectedCallback(int priority, JadeTooltipCollectedCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addTooltipCollectedCallback(priority, callback);
		} else {
			tooltipCollectedCallback.add(priority, callback);
		}
	}

	@Override
	public void addItemModNameCallback(int priority, JadeItemModNameCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addItemModNameCallback(priority, callback);
		} else {
			itemModNameCallback.add(priority, callback);
		}
	}

	@Override
	public void addBeforeTooltipCollectCallback(int priority, JadeBeforeTooltipCollectCallback callback) {
		Objects.requireNonNull(callback);
		if (isSessionActive()) {
			session.addBeforeTooltipCollectCallback(priority, callback);
		} else {
			beforeTooltipCollectCallback.add(priority, callback);
		}
	}

	@Override
	public BlockAccessor.Builder blockAccessor() {
		Minecraft mc = Minecraft.getInstance();
		/* off */
		return new BlockAccessorImpl.Builder().level(mc.level).player(mc.player).serverConnected(isServerConnected()).serverData(
				getServerData()).showDetails(isShowDetailsPressed());
		/* on */
	}

	@Override
	public EntityAccessor.Builder entityAccessor() {
		Minecraft mc = Minecraft.getInstance();
		/* off */
		return new EntityAccessorImpl.Builder().level(mc.level).player(mc.player).serverConnected(isServerConnected()).serverData(
				getServerData()).showDetails(isShowDetailsPressed());
		/* on */
	}

	@Override
	public void registerCustomEnchantPower(Block block, CustomEnchantPower customEnchantPower) {
		customEnchantPowers.put(block, customEnchantPower);
	}

	@Override
	public Screen createPluginConfigScreen(@Nullable Screen parent, @Nullable Component jumpToCategory) {
		Function<OptionsList, OptionsList.Entry> jumpTo = null;
		if (jumpToCategory != null) {
			String title = jumpToCategory.getString();
			jumpTo = options -> {
				for (OptionsList.Entry entry : options.children()) {
					if (entry instanceof OptionsList.Title e && e.getTitle().getString().equals(title)) {
						return entry;
					}
				}
				return null;
			};
		}
		return PluginsConfigScreen.createPluginConfigScreen(parent, jumpTo, false);
	}

	@Override
	public void registerItemStorageClient(IClientExtensionProvider<ItemStack, ItemView> provider) {
		Objects.requireNonNull(provider.getUid());
		if (isSessionActive()) {
			session.registerItemStorageClient(provider);
		} else {
			itemStorageProviders.put(provider.getUid(), provider);
		}
	}

	@Override
	public void registerFluidStorageClient(IClientExtensionProvider<CompoundTag, FluidView> provider) {
		Objects.requireNonNull(provider.getUid());
		if (isSessionActive()) {
			session.registerFluidStorageClient(provider);
		} else {
			fluidStorageProviders.put(provider.getUid(), provider);
		}
	}

	@Override
	public void registerEnergyStorageClient(IClientExtensionProvider<CompoundTag, EnergyView> provider) {
		Objects.requireNonNull(provider.getUid());
		if (isSessionActive()) {
			session.registerEnergyStorageClient(provider);
		} else {
			energyStorageProviders.put(provider.getUid(), provider);
		}
	}

	@Override
	public void registerProgressClient(IClientExtensionProvider<CompoundTag, ProgressView> provider) {
		Objects.requireNonNull(provider.getUid());
		if (isSessionActive()) {
			session.registerProgressClient(provider);
		} else {
			progressProviders.put(provider.getUid(), provider);
		}
	}

	@Override
	public boolean isServerConnected() {
		return ObjectDataCenter.serverConnected;
	}

	@Override
	public boolean isShowDetailsPressed() {
		return ClientProxy.isShowDetailsPressed();
	}

	@Override
	public CompoundTag getServerData() {
		return ObjectDataCenter.getServerData();
	}

	@Override
	public void setServerData(CompoundTag tag) {
		ObjectDataCenter.setServerData(tag);
	}

	@Override
	public ItemStack getBlockCamouflage(LevelAccessor level, BlockPos pos) {
		return DatapackBlockManager.getFakeBlock(level, pos);
	}

	@Override
	public void markAsClientFeature(ResourceLocation uid) {
		clientFeatures.add(uid);
	}

	@Override
	public void markAsServerFeature(ResourceLocation uid) {
		clientFeatures.remove(uid);
	}

	@Override
	public boolean isClientFeature(ResourceLocation uid) {
		return clientFeatures.contains(uid);
	}

	@Override
	@SuppressWarnings("unchecked")
	public <T extends Accessor<?>> void registerAccessorHandler(Class<T> clazz, AccessorClientHandler<T> handler) {
		accessorHandlers.put((Class<Accessor<?>>) clazz, (AccessorClientHandler<Accessor<?>>) handler);
	}

	@Override
	public AccessorClientHandler<Accessor<?>> getAccessorHandler(Class<? extends Accessor<?>> clazz) {
		return Objects.requireNonNull(accessorHandlers.get(clazz), () -> "No accessor handler for " + clazz);
	}

	@Override
	public boolean maybeLowVisionUser() {
		return ClientProxy.hasAccessibilityMod() || IWailaConfig.get().accessibility().shouldEnableTextToSpeech();
	}

	public void startSession() {
		if (session == null) {
			session = new ClientRegistrationSession(this);
		}
		session.reset();
	}

	public void endSession() {
		Preconditions.checkState(session != null, "Session not started");
		session.end();
	}

	public boolean isSessionActive() {
		return session != null && session.isActive();
	}
}

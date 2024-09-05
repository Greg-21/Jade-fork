package snownee.jade;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.config.IWailaConfig;
import snownee.jade.impl.WailaClientRegistration;
import snownee.jade.impl.WailaCommonRegistration;
import snownee.jade.impl.config.PluginConfig;
import snownee.jade.impl.config.WailaConfig;
import snownee.jade.test.ExamplePlugin;
import snownee.jade.util.CommonProxy;
import snownee.jade.util.JsonConfig;

public class Jade {
	public static final String ID = "jade";
	public static final Logger LOGGER = LogUtils.getLogger();
	/**
	 * addons: Use {@link IWailaConfig#get()}
	 */
	public static final JsonConfig<WailaConfig> CONFIG = new JsonConfig<>(Jade.ID + "/" + Jade.ID, WailaConfig.CODEC, null);
	public static boolean FROZEN;

	public static void loadComplete() {
		if (FROZEN) {
			return;
		}
		FROZEN = true;
		if (CommonProxy.isDevEnv()) {
			try {
				IWailaPlugin plugin = new ExamplePlugin();
				plugin.register(WailaCommonRegistration.instance());
				if (CommonProxy.isPhysicallyClient()) {
					plugin.registerClient(WailaClientRegistration.instance());
				}
			} catch (Throwable ignored) {
			}
		}

		WailaCommonRegistration.instance().priorities.sort(PluginConfig.INSTANCE.getKeys());
		WailaCommonRegistration.instance().loadComplete();
		if (CommonProxy.isPhysicallyClient()) {
			WailaClientRegistration.instance().loadComplete();
			WailaConfig.init();
		}
		PluginConfig.INSTANCE.reload();
	}
}

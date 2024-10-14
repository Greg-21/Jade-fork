package snownee.jade.test;

import java.util.List;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import snownee.jade.api.Accessor;
import snownee.jade.api.view.ClientViewGroup;
import snownee.jade.api.view.IClientExtensionProvider;
import snownee.jade.api.view.IServerExtensionProvider;
import snownee.jade.api.view.ProgressView;
import snownee.jade.api.view.ViewGroup;

public enum ExampleProgressProvider implements IServerExtensionProvider<ProgressView.Data>,
		IClientExtensionProvider<ProgressView.Data, ProgressView> {
	INSTANCE;

	@Override
	public ResourceLocation getUid() {
		return ExamplePlugin.UID_TEST_PROGRESS;
	}

	@Override
	public List<ClientViewGroup<ProgressView>> getClientGroups(Accessor<?> accessor, List<ViewGroup<ProgressView.Data>> groups) {
		return ClientViewGroup.map(groups, ProgressView::read, (group, clientGroup) -> {
			var view = clientGroup.views.getFirst();
			view.style.color(0xFFCC0000);
			view.text = Component.literal("Testtttttttttttttttttttttttttttttttt");

			view = clientGroup.views.get(1);
			view.style.color(0xFF00CC00);
			view.text = Component.literal("Test");
		});
	}

	@Override
	public List<ViewGroup<ProgressView.Data>> getGroups(Accessor<?> accessor) {
		Level world = accessor.getLevel();
		float period = 40;
		var progress1 = new ProgressView.Data(((world.getGameTime() % period) + 1) / period);
		period = 200;
		var progress2 = new ProgressView.Data(((world.getGameTime() % period) + 1) / period);
		var group = new ViewGroup<>(List.of(progress1, progress2));
		return List.of(group);
	}
}

package com.timlee9024.mcgltf;

import java.util.List;

import de.javagl.jgltf.model.GltfModel;
import net.minecraft.util.ResourceLocation;

public interface IGltfModelReceiver {

	ResourceLocation getModelLocation();
	
	@Deprecated
	default void onModelLoaded(RenderedGltfModel renderedModel) {}
	
	default void onReceiveSharedModel(RenderedGltfModel renderedModel) {
		onModelLoaded(renderedModel);
	}
	
	default boolean isReceiveSharedModel(GltfModel gltfModel, List<GltfRenderData> gltfRenderDatas) {
		return true;
	}
}

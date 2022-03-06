package com.timlee9024.mcgltf;

import net.minecraft.util.ResourceLocation;

public interface IGltfModelReceiver {

	ResourceLocation getModelLocation();
	
	void onModelLoaded(RenderedGltfModel renderedModel);
}

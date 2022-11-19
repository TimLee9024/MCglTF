package com.modularmods.mcgltf.iris;

import java.util.List;

import com.google.gson.Gson;
import com.modularmods.mcgltf.RenderedGltfModelGL30;

import de.javagl.jgltf.model.GltfModel;

public class RenderedGltfModelGL30Iris extends RenderedGltfModelGL30 {

	public RenderedGltfModelGL30Iris(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfRenderData, gltfModel);
	}

	@Override
	public Material obtainMaterial(List<Runnable> gltfRenderData, Object extras) {
		Material material = extrasToMaterial.get(extras);
		if(material == null) {
			Gson gson = new Gson();
			material = gson.fromJson(gson.toJsonTree(extras), RenderedGltfModelIris.MaterialIris.class);
			material.initMaterialCommand(gltfRenderData, this);
			extrasToMaterial.put(extras, material);
		}
		return material;
	}

}

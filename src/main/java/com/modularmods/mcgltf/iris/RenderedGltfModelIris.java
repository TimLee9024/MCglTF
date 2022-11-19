package com.modularmods.mcgltf.iris;

import java.util.List;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import com.google.gson.Gson;
import com.modularmods.mcgltf.MCglTF;
import com.modularmods.mcgltf.RenderedGltfModel;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.TextureModel;

public class RenderedGltfModelIris extends RenderedGltfModel {

	public RenderedGltfModelIris(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfRenderData, gltfModel);
	}

	@Override
	public Material obtainMaterial(List<Runnable> gltfRenderData, Object extras) {
		Material material = extrasToMaterial.get(extras);
		if(material == null) {
			Gson gson = new Gson();
			material = gson.fromJson(gson.toJsonTree(extras), MaterialIris.class);
			material.initMaterialCommand(gltfRenderData, this);
			extrasToMaterial.put(extras, material);
		}
		return material;
	}
	
	public static class MaterialIris extends Material {

		@Override
		public void initMaterialCommand(List<Runnable> gltfRenderData, RenderedGltfModel renderedModel) {
			List<TextureModel> textureModels = renderedModel.gltfModel.getTextureModels();
			int colorMap = baseColorTexture == null ? MCglTF.getInstance().getDefaultColorMap() : renderedModel.obtainGlTexture(gltfRenderData, textureModels.get(baseColorTexture.index));
			int normalMap = normalTexture == null ? MCglTF.getInstance().getDefaultNormalMap() : renderedModel.obtainGlTexture(gltfRenderData, textureModels.get(normalTexture.index));
			int specularMap = specularTexture == null ? MCglTF.getInstance().getDefaultSpecularMap() : renderedModel.obtainGlTexture(gltfRenderData, textureModels.get(specularTexture.index));
			if(doubleSided) {
				vanillaMaterialCommand = () -> {
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMap);
					GL11.glColor4f(baseColorFactor[0], baseColorFactor[1], baseColorFactor[2], baseColorFactor[3]);
					GL11.glDisable(GL11.GL_CULL_FACE);
				};
				shaderModMaterialCommand = () -> {
					GL13.glActiveTexture(COLOR_MAP_INDEX);
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMap);
					if(NORMAL_MAP_INDEX != -1) {
						GL13.glActiveTexture(NORMAL_MAP_INDEX);
						GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalMap);
					}
					if(SPECULAR_MAP_INDEX != -1) {
						GL13.glActiveTexture(SPECULAR_MAP_INDEX);
						GL11.glBindTexture(GL11.GL_TEXTURE_2D, specularMap);
					}
					GL11.glColor4f(baseColorFactor[0], baseColorFactor[1], baseColorFactor[2], baseColorFactor[3]);
					GL11.glDisable(GL11.GL_CULL_FACE);
				};
			}
			else {
				vanillaMaterialCommand = () -> {
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMap);
					GL11.glColor4f(baseColorFactor[0], baseColorFactor[1], baseColorFactor[2], baseColorFactor[3]);
					GL11.glEnable(GL11.GL_CULL_FACE);
				};
				shaderModMaterialCommand = () -> {
					GL13.glActiveTexture(COLOR_MAP_INDEX);
					GL11.glBindTexture(GL11.GL_TEXTURE_2D, colorMap);
					if(NORMAL_MAP_INDEX != -1) {
						GL13.glActiveTexture(NORMAL_MAP_INDEX);
						GL11.glBindTexture(GL11.GL_TEXTURE_2D, normalMap);
					}
					if(SPECULAR_MAP_INDEX != -1) {
						GL13.glActiveTexture(SPECULAR_MAP_INDEX);
						GL11.glBindTexture(GL11.GL_TEXTURE_2D, specularMap);
					}
					GL11.glColor4f(baseColorFactor[0], baseColorFactor[1], baseColorFactor[2], baseColorFactor[3]);
					GL11.glEnable(GL11.GL_CULL_FACE);
				};
			}
		}
		
	}

}

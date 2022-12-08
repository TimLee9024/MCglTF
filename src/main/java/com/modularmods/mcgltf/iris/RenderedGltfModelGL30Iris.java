package com.modularmods.mcgltf.iris;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL20;

import com.google.gson.Gson;
import com.modularmods.mcgltf.RenderedGltfModelGL30;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;

public class RenderedGltfModelGL30Iris extends RenderedGltfModelGL30 {

	public RenderedGltfModelGL30Iris(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfRenderData, gltfModel);
	}

	@Override
	protected void processSceneModels(List<Runnable> gltfRenderData, List<SceneModel> sceneModels) {
		for(SceneModel sceneModel : sceneModels) {
			RenderedGltfSceneGL30Iris renderedGltfScene = new RenderedGltfSceneGL30Iris();
			renderedGltfScenes.add(renderedGltfScene);
			
			for(NodeModel nodeModel : sceneModel.getNodeModels()) {
				Triple<List<Runnable>, List<Runnable>, List<Runnable>> commands = rootNodeModelToCommands.get(nodeModel);
				List<Runnable> vanillaRootRenderCommands;
				List<Runnable> shaderModRootRenderCommands;
				if(commands == null) {
					vanillaRootRenderCommands = new ArrayList<Runnable>();
					shaderModRootRenderCommands = new ArrayList<Runnable>();
					processNodeModel(gltfRenderData, nodeModel, vanillaRootRenderCommands, shaderModRootRenderCommands);
					rootNodeModelToCommands.put(nodeModel, Triple.of(null, vanillaRootRenderCommands, shaderModRootRenderCommands));
				}
				else {
					vanillaRootRenderCommands = commands.getMiddle();
					shaderModRootRenderCommands = commands.getRight();
				}
				renderedGltfScene.vanillaRenderCommands.addAll(vanillaRootRenderCommands);
				renderedGltfScene.shaderModRenderCommands.addAll(shaderModRootRenderCommands);
			}
		}
	}

	@Override
	protected void applyTransformShaderMod(NodeModel nodeModel) {
		float[] transform = findGlobalTransform(nodeModel);
		Matrix4f pose = new Matrix4f();
		pose.setTransposed(transform);
		
		if(NORMAL_MATRIX != -1) {
			Matrix3f normal = new Matrix3f(pose);
			normal.transpose();
			normal.mulLocal(CURRENT_NORMAL);
			
			normal.get(BUF_FLOAT_9);
			GL20.glUniformMatrix3fv(NORMAL_MATRIX, false, BUF_FLOAT_9);
		}
		
		pose.transpose();
		pose.mulLocal(CURRENT_POSE);
		
		pose.get(BUF_FLOAT_16);
		GL20.glUniformMatrix4fv(MODEL_VIEW_MATRIX, false, BUF_FLOAT_16);
	}

	@Override
	public Material obtainMaterial(List<Runnable> gltfRenderData, MaterialModel materialModel) {
		Material material = materialModelToRenderedMaterial.get(materialModel);
		if(material == null) {
			Object extras = materialModel.getExtras();
			if(extras != null) {
				Gson gson = new Gson();
				material = gson.fromJson(gson.toJsonTree(extras), RenderedGltfModelIris.MaterialIris.class);
			}
			else material = new RenderedGltfModelIris.MaterialIris();
			material.initMaterialCommand(gltfRenderData, this, materialModel);
			materialModelToRenderedMaterial.put(materialModel, material);
		}
		return material;
	}

}

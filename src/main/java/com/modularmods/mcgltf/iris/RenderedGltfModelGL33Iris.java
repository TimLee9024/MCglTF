package com.modularmods.mcgltf.iris;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;

import com.google.gson.Gson;
import com.modularmods.mcgltf.RenderedGltfModelGL33;
import com.modularmods.mcgltf.mixin.Matrix4fAccessor;
import com.mojang.math.Matrix4f;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;

public class RenderedGltfModelGL33Iris extends RenderedGltfModelGL33 {

	public RenderedGltfModelGL33Iris(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfRenderData, gltfModel);
	}

	@Override
	protected void processSceneModels(List<Runnable> gltfRenderData, List<SceneModel> sceneModels) {
		for(SceneModel sceneModel : sceneModels) {
			RenderedGltfSceneGL33Iris renderedGltfScene = new RenderedGltfSceneGL33Iris();
			renderedGltfScenes.add(renderedGltfScene);
			
			for(NodeModel nodeModel : sceneModel.getNodeModels()) {
				Triple<List<Runnable>, List<Runnable>, List<Runnable>> commands = rootNodeModelToCommands.get(nodeModel);
				List<Runnable> rootSkinningCommands;
				List<Runnable> vanillaRootRenderCommands;
				List<Runnable> shaderModRootRenderCommands;
				if(commands == null) {
					rootSkinningCommands = new ArrayList<Runnable>();
					vanillaRootRenderCommands = new ArrayList<Runnable>();
					shaderModRootRenderCommands = new ArrayList<Runnable>();
					processNodeModel(gltfRenderData, nodeModel, rootSkinningCommands, vanillaRootRenderCommands, shaderModRootRenderCommands);
					rootNodeModelToCommands.put(nodeModel, Triple.of(rootSkinningCommands, vanillaRootRenderCommands, shaderModRootRenderCommands));
				}
				else {
					rootSkinningCommands = commands.getLeft();
					vanillaRootRenderCommands = commands.getMiddle();
					shaderModRootRenderCommands = commands.getRight();
				}
				renderedGltfScene.skinningCommands.addAll(rootSkinningCommands);
				renderedGltfScene.vanillaRenderCommands.addAll(vanillaRootRenderCommands);
				renderedGltfScene.shaderModRenderCommands.addAll(shaderModRootRenderCommands);
			}
		}
	}

	@Override
	protected void applyTransformShaderMod(NodeModel nodeModel) {
		Matrix4f pose = new Matrix4f();
		float[] transform = findGlobalTransform(nodeModel);
		Matrix4fAccessor accessor = (Matrix4fAccessor)(Object) pose;
		accessor.setM00(transform[0]);
		accessor.setM01(transform[1]);
		accessor.setM02(transform[2]);
		accessor.setM03(transform[3]);
		accessor.setM10(transform[4]);
		accessor.setM11(transform[5]);
		accessor.setM12(transform[6]);
		accessor.setM13(transform[7]);
		accessor.setM20(transform[8]);
		accessor.setM21(transform[9]);
		accessor.setM22(transform[10]);
		accessor.setM23(transform[11]);
		accessor.setM30(transform[12]);
		accessor.setM31(transform[13]);
		accessor.setM32(transform[14]);
		accessor.setM33(transform[15]);
		
		pose.transpose();
		Matrix4f currentPose = CURRENT_POSE.copy();
		currentPose.multiply(pose);
		
		CURRENT_SHADER_INSTANCE.MODEL_VIEW_MATRIX.set(currentPose);
		CURRENT_SHADER_INSTANCE.MODEL_VIEW_MATRIX.upload();
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

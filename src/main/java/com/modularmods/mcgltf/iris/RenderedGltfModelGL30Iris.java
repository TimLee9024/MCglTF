package com.modularmods.mcgltf.iris;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.opengl.GL20;

import com.google.gson.Gson;
import com.modularmods.mcgltf.RenderedGltfModelGL30;
import com.modularmods.mcgltf.mixin.Matrix4fAccessor;
import com.mojang.math.Matrix3f;
import com.mojang.math.Matrix4f;

import de.javagl.jgltf.model.GltfModel;
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

		if(NORMAL_MATRIX != -1) {
			Matrix3f normal = new Matrix3f(pose);
			normal.transpose();
			Matrix3f currentNormal = CURRENT_NORMAL.copy();
			currentNormal.mul(normal);
			
			currentNormal.store(BUF_FLOAT_9);
			GL20.glUniformMatrix3fv(NORMAL_MATRIX, false, BUF_FLOAT_9);
		}
		
		pose.transpose();
		Matrix4f currentPose = CURRENT_POSE.copy();
		currentPose.multiply(pose);
		
		currentPose.store(BUF_FLOAT_16);
		GL20.glUniformMatrix4fv(MODEL_VIEW_MATRIX, false, BUF_FLOAT_16);
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

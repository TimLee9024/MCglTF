package com.modularmods.mcgltf;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import de.javagl.jgltf.model.AccessorByteData;
import de.javagl.jgltf.model.AccessorData;
import de.javagl.jgltf.model.AccessorDatas;
import de.javagl.jgltf.model.AccessorFloatData;
import de.javagl.jgltf.model.AccessorIntData;
import de.javagl.jgltf.model.AccessorModel;
import de.javagl.jgltf.model.AccessorShortData;
import de.javagl.jgltf.model.ElementType;
import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.MathUtils;
import de.javagl.jgltf.model.MeshModel;
import de.javagl.jgltf.model.MeshPrimitiveModel;
import de.javagl.jgltf.model.NodeModel;
import de.javagl.jgltf.model.SceneModel;
import de.javagl.jgltf.model.SkinModel;

public class RenderedGltfModelGL30 extends RenderedGltfModel {

	protected final Map<AccessorModel, AccessorModel> jointsAccessorModelUnsignedLookup = new IdentityHashMap<AccessorModel, AccessorModel>();
	protected final Map<AccessorModel, AccessorModel> weightsAccessorModelDequantizedLookup = new IdentityHashMap<AccessorModel, AccessorModel>();
	
	public RenderedGltfModelGL30(List<Runnable> gltfRenderData, GltfModel gltfModel) {
		super(gltfModel, new ArrayList<RenderedGltfScene>(gltfModel.getSceneModels().size())); //Need to use this parent constructor to init final fields of this class before processSceneModels
		processSceneModels(gltfRenderData, gltfModel.getSceneModels());
	}
	
	@Override
	protected void processSceneModels(List<Runnable> gltfRenderData, List<SceneModel> sceneModels) {
		for(SceneModel sceneModel : sceneModels) {
			RenderedGltfScene renderedGltfScene = new RenderedGltfSceneGL30();
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
	
	protected void processNodeModel(List<Runnable> gltfRenderData, NodeModel nodeModel, List<Runnable> vanillaRenderCommands, List<Runnable> shaderModRenderCommands) {
		ArrayList<Runnable> vanillaNodeRenderCommands = new ArrayList<Runnable>();
		ArrayList<Runnable> shaderModNodeRenderCommands = new ArrayList<Runnable>();
		SkinModel skinModel = nodeModel.getSkinModel();
		if(skinModel != null) {
			int jointCount = skinModel.getJoints().size();
			
			float[][] transforms = new float[jointCount][];
			float[] invertNodeTransform = new float[16];
			float[] bindShapeMatrix = new float[16];
			
			List<Runnable> jointMatricesTransformCommands = new ArrayList<Runnable>(jointCount);
			for(int joint = 0; joint < jointCount; joint++) {
				int i = joint;
				float[] transform = transforms[i] = new float[16];
				float[] inverseBindMatrix = new float[16];
				jointMatricesTransformCommands.add(() -> {
					MathUtils.mul4x4(invertNodeTransform, transform, transform);
					skinModel.getInverseBindMatrix(i, inverseBindMatrix);
					MathUtils.mul4x4(transform, inverseBindMatrix, transform);
					MathUtils.mul4x4(transform, bindShapeMatrix, transform);
					MathUtils.transpose4x4(transform, transform);
				});
			}
			
			Runnable jointMatricesTransformCommand = () -> {
				for(int i = 0; i < transforms.length; i++) {
					System.arraycopy(findGlobalTransform(skinModel.getJoints().get(i)), 0, transforms[i], 0, 16);
				}
				MathUtils.invert4x4(findGlobalTransform(nodeModel), invertNodeTransform);
				skinModel.getBindShapeMatrix(bindShapeMatrix);
				jointMatricesTransformCommands.parallelStream().forEach(Runnable::run);
			};
			vanillaNodeRenderCommands.add(jointMatricesTransformCommand);
			shaderModNodeRenderCommands.add(jointMatricesTransformCommand);
			
			for(MeshModel meshModel : nodeModel.getMeshModels()) {
				for(MeshPrimitiveModel meshPrimitiveModel : meshModel.getMeshPrimitiveModels()) {
					processMeshPrimitiveModel(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, transforms, vanillaNodeRenderCommands, shaderModNodeRenderCommands);
				}
			}
		}
		else {
			if(!nodeModel.getMeshModels().isEmpty()) {
				for(MeshModel meshModel : nodeModel.getMeshModels()) {
					for(MeshPrimitiveModel meshPrimitiveModel : meshModel.getMeshPrimitiveModels()) {
						processMeshPrimitiveModel(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, vanillaNodeRenderCommands, shaderModNodeRenderCommands);
					}
				}
			}
		}
		nodeModel.getChildren().forEach((childNode) -> processNodeModel(gltfRenderData, childNode, vanillaNodeRenderCommands, shaderModNodeRenderCommands));
		if(!vanillaNodeRenderCommands.isEmpty()) {
			vanillaRenderCommands.add(() -> {
				float[] scale = nodeModel.getScale();
				if(scale == null || scale[0] != 0.0F || scale[1] != 0.0F || scale[2] != 0.0F) {
					applyTransformVanilla(nodeModel);
					
					vanillaNodeRenderCommands.forEach(Runnable::run);
				}
			});
			shaderModRenderCommands.add(() -> {
				float[] scale = nodeModel.getScale();
				if(scale == null || scale[0] != 0.0F || scale[1] != 0.0F || scale[2] != 0.0F) {
					applyTransformShaderMod(nodeModel);
					
					shaderModNodeRenderCommands.forEach(Runnable::run);
				}
			});
		}
	}
	
	protected void processMeshPrimitiveModel(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, float[][] jointMatrices, List<Runnable> vanillaRenderCommands, List<Runnable> shaderModRenderCommands) {
		Map<String, AccessorModel> attributes = meshPrimitiveModel.getAttributes();
		AccessorModel positionsAccessorModel = attributes.get("POSITION");
		if(positionsAccessorModel != null) {
			List<Runnable> renderCommand = new ArrayList<Runnable>();
			AccessorModel normalsAccessorModel = attributes.get("NORMAL");
			if(normalsAccessorModel != null) {
				AccessorModel tangentsAccessorModel = attributes.get("TANGENT");
				if(tangentsAccessorModel != null) {
					processMeshPrimitiveModelIncludedTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices, attributes, positionsAccessorModel, normalsAccessorModel, tangentsAccessorModel);
					MaterialModel materialModel = meshPrimitiveModel.getMaterialModel();
					if(materialModel != null) {
						Material renderedMaterial = obtainMaterial(gltfRenderData, materialModel);
						vanillaRenderCommands.add(renderedMaterial.vanillaMaterialCommand);
						shaderModRenderCommands.add(renderedMaterial.shaderModMaterialCommand);
					}
					else {
						vanillaRenderCommands.add(vanillaDefaultMaterialCommand);
						shaderModRenderCommands.add(shaderModDefaultMaterialCommand);
					}
				}
				else {
					MaterialModel materialModel = meshPrimitiveModel.getMaterialModel();
					if(materialModel != null) {
						Material renderedMaterial = obtainMaterial(gltfRenderData, materialModel);
						vanillaRenderCommands.add(renderedMaterial.vanillaMaterialCommand);
						shaderModRenderCommands.add(renderedMaterial.shaderModMaterialCommand);
						if(renderedMaterial.normalTexture != null) {
							processMeshPrimitiveModelMikkTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices);
						}
						else {
							processMeshPrimitiveModelSimpleTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices, attributes, positionsAccessorModel, normalsAccessorModel);
						}
					}
					else {
						vanillaRenderCommands.add(vanillaDefaultMaterialCommand);
						shaderModRenderCommands.add(shaderModDefaultMaterialCommand);
						processMeshPrimitiveModelSimpleTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices, attributes, positionsAccessorModel, normalsAccessorModel);
					}
				}
			}
			else {
				MaterialModel materialModel = meshPrimitiveModel.getMaterialModel();
				if(materialModel != null) {
					Material renderedMaterial = obtainMaterial(gltfRenderData, materialModel);
					vanillaRenderCommands.add(renderedMaterial.vanillaMaterialCommand);
					shaderModRenderCommands.add(renderedMaterial.shaderModMaterialCommand);
					if(renderedMaterial.normalTexture != null) {
						processMeshPrimitiveModelFlatNormalMikkTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices);
					}
					else {
						processMeshPrimitiveModelFlatNormalSimpleTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices);
					}
				}
				else {
					vanillaRenderCommands.add(vanillaDefaultMaterialCommand);
					shaderModRenderCommands.add(shaderModDefaultMaterialCommand);
					processMeshPrimitiveModelFlatNormalSimpleTangent(gltfRenderData, nodeModel, meshModel, meshPrimitiveModel, renderCommand, jointMatrices);
				}
			}
			vanillaRenderCommands.addAll(renderCommand);
			shaderModRenderCommands.addAll(renderCommand);
		}
	}
	
	protected void processMeshPrimitiveModelIncludedTangent(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, List<Runnable> renderCommand, float[][] jointMatrices, Map<String, AccessorModel> attributes, AccessorModel positionsAccessorModel, AccessorModel normalsAccessorModel, AccessorModel tangentsAccessorModel) {
		List<Map<String, AccessorModel>> morphTargets = meshPrimitiveModel.getTargets();
		
		AccessorModel outputPositionsAccessorModel;
		List<AccessorFloatData> targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "POSITION")) {
			outputPositionsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, positionsAccessorModel, targetAccessorDatas);
		}
		else {
			outputPositionsAccessorModel = AccessorModelCreation.instantiate(positionsAccessorModel, "");
		}
		
		AccessorModel outputNormalsAccessorModel;
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "NORMAL")) {
			outputNormalsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, normalsAccessorModel, targetAccessorDatas);
		}
		else {
			outputNormalsAccessorModel = AccessorModelCreation.instantiate(normalsAccessorModel, "");
		}
		
		AccessorModel outputTangentsAccessorModel;
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "TANGENT")) {
			outputTangentsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, tangentsAccessorModel, targetAccessorDatas);
		}
		else {
			outputTangentsAccessorModel = AccessorModelCreation.instantiate(tangentsAccessorModel, "");
		}
		
		int pointCount = positionsAccessorModel.getCount();
		List<Runnable> skinningCommands = createSoftwareSkinningCommands(pointCount, jointMatrices,
				AccessorDatas.createInt(obtainUnsignedJointsModel(attributes.get("JOINTS_0"))),
				AccessorDatas.createFloat(obtainDequantizedWeightsModel(attributes.get("WEIGHTS_0"))),
				AccessorDatas.createFloat(positionsAccessorModel),
				AccessorDatas.createFloat(normalsAccessorModel),
				AccessorDatas.createFloat(tangentsAccessorModel),
				AccessorDatas.createFloat(outputPositionsAccessorModel),
				AccessorDatas.createFloat(outputNormalsAccessorModel),
				AccessorDatas.createFloat(outputTangentsAccessorModel));
		
		int glVertexArray = GL30.glGenVertexArrays();
		gltfRenderData.add(() -> GL30.glDeleteVertexArrays(glVertexArray));
		GL30.glBindVertexArray(glVertexArray);
		
		int positionBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(positionBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputPositionsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaPosition,
				outputPositionsAccessorModel.getElementType().getNumComponents(),
				outputPositionsAccessorModel.getComponentType(),
				false,
				outputPositionsAccessorModel.getByteStride(),
				outputPositionsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaPosition);
		
		int normalBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(normalBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputNormalsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaNormal,
				outputNormalsAccessorModel.getElementType().getNumComponents(),
				outputNormalsAccessorModel.getComponentType(),
				false,
				outputNormalsAccessorModel.getByteStride(),
				outputNormalsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaNormal);
		
		int tangentBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(tangentBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputTangentsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				at_tangent,
				outputTangentsAccessorModel.getElementType().getNumComponents(),
				outputTangentsAccessorModel.getComponentType(),
				false,
				outputTangentsAccessorModel.getByteStride(),
				outputTangentsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(at_tangent);
		
		AccessorModel colorsAccessorModel = attributes.get("COLOR_0");
		if(colorsAccessorModel != null) {
			colorsAccessorModel = obtainVec4ColorsAccessorModel(colorsAccessorModel);
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createColorMorphTarget(morphTargets, targetAccessorDatas)) {
				colorsAccessorModel = bindColorMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, colorsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, colorsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaColor,
					colorsAccessorModel.getElementType().getNumComponents(),
					colorsAccessorModel.getComponentType(),
					false,
					colorsAccessorModel.getByteStride(),
					colorsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaColor);
		}
		
		AccessorModel texcoordsAccessorModel = attributes.get("TEXCOORD_0");
		if(texcoordsAccessorModel != null) {
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createTexcoordMorphTarget(morphTargets, targetAccessorDatas)) {
				texcoordsAccessorModel = bindTexcoordMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, texcoordsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, texcoordsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaUV0,
					texcoordsAccessorModel.getElementType().getNumComponents(),
					texcoordsAccessorModel.getComponentType(),
					false,
					texcoordsAccessorModel.getByteStride(),
					texcoordsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaUV0);
		}
		
		ByteBuffer positionsBufferViewData = outputPositionsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer normalsBufferViewData = outputNormalsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer tangentsBufferViewData = outputTangentsAccessorModel.getBufferViewModel().getBufferViewData();
		
		int mode = meshPrimitiveModel.getMode();
		AccessorModel indices = meshPrimitiveModel.getIndices();
		if(indices != null) {
			int glIndicesBufferView = obtainElementArrayBuffer(gltfRenderData, indices.getBufferViewModel());
			int count = indices.getCount();
			int type = indices.getComponentType();
			int offset = indices.getByteOffset();
			renderCommand.add(() -> {
				skinningCommands.parallelStream().forEach(Runnable::run);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
				
				GL30.glBindVertexArray(glVertexArray);
				GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, glIndicesBufferView);
				GL11.glDrawElements(mode, count, type, offset);
			});
		}
		else {
			renderCommand.add(() -> {
				skinningCommands.parallelStream().forEach(Runnable::run);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
				
				GL30.glBindVertexArray(glVertexArray);
				GL11.glDrawArrays(mode, 0, pointCount);
			});
		}
	}
	
	protected void processMeshPrimitiveModelSimpleTangent(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, List<Runnable> renderCommand, float[][] jointMatrices, Map<String, AccessorModel> attributes, AccessorModel positionsAccessorModel, AccessorModel normalsAccessorModel) {
		List<Map<String, AccessorModel>> morphTargets = meshPrimitiveModel.getTargets();
		
		AccessorModel outputPositionsAccessorModel;
		List<AccessorFloatData> targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "POSITION")) {
			outputPositionsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, positionsAccessorModel, targetAccessorDatas);
		}
		else {
			outputPositionsAccessorModel = AccessorModelCreation.instantiate(positionsAccessorModel, "");
		}
		
		AccessorModel tangentsAccessorModel = obtainTangentsAccessorModel(normalsAccessorModel);
		AccessorModel outputNormalsAccessorModel;
		AccessorModel outputTangentsAccessorModel;
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		List<AccessorFloatData> tangentTargetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createNormalTangentMorphTarget(morphTargets, normalsAccessorModel, tangentsAccessorModel, targetAccessorDatas, tangentTargetAccessorDatas)) {
			outputNormalsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, normalsAccessorModel, targetAccessorDatas);
			outputTangentsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, tangentsAccessorModel, targetAccessorDatas);
		}
		else {
			outputNormalsAccessorModel = AccessorModelCreation.instantiate(normalsAccessorModel, "");
			outputTangentsAccessorModel = AccessorModelCreation.instantiate(tangentsAccessorModel, "");
		}
		
		int pointCount = positionsAccessorModel.getCount();
		List<Runnable> skinningCommands = createSoftwareSkinningCommands(pointCount, jointMatrices,
				AccessorDatas.createInt(obtainUnsignedJointsModel(attributes.get("JOINTS_0"))),
				AccessorDatas.createFloat(obtainDequantizedWeightsModel(attributes.get("WEIGHTS_0"))),
				AccessorDatas.createFloat(positionsAccessorModel),
				AccessorDatas.createFloat(normalsAccessorModel),
				AccessorDatas.createFloat(tangentsAccessorModel),
				AccessorDatas.createFloat(outputPositionsAccessorModel),
				AccessorDatas.createFloat(outputNormalsAccessorModel),
				AccessorDatas.createFloat(outputTangentsAccessorModel));
		
		int glVertexArray = GL30.glGenVertexArrays();
		gltfRenderData.add(() -> GL30.glDeleteVertexArrays(glVertexArray));
		GL30.glBindVertexArray(glVertexArray);
		
		int positionBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(positionBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputPositionsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaPosition,
				outputPositionsAccessorModel.getElementType().getNumComponents(),
				outputPositionsAccessorModel.getComponentType(),
				false,
				outputPositionsAccessorModel.getByteStride(),
				outputPositionsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaPosition);
		
		int normalBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(normalBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputNormalsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaNormal,
				outputNormalsAccessorModel.getElementType().getNumComponents(),
				outputNormalsAccessorModel.getComponentType(),
				false,
				outputNormalsAccessorModel.getByteStride(),
				outputNormalsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaNormal);
		
		int tangentBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(tangentBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputTangentsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				at_tangent,
				outputTangentsAccessorModel.getElementType().getNumComponents(),
				outputTangentsAccessorModel.getComponentType(),
				false,
				outputTangentsAccessorModel.getByteStride(),
				outputTangentsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(at_tangent);
		
		AccessorModel colorsAccessorModel = attributes.get("COLOR_0");
		if(colorsAccessorModel != null) {
			colorsAccessorModel = obtainVec4ColorsAccessorModel(colorsAccessorModel);
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createColorMorphTarget(morphTargets, targetAccessorDatas)) {
				colorsAccessorModel = bindColorMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, colorsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, colorsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaColor,
					colorsAccessorModel.getElementType().getNumComponents(),
					colorsAccessorModel.getComponentType(),
					false,
					colorsAccessorModel.getByteStride(),
					colorsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaColor);
		}
		
		AccessorModel texcoordsAccessorModel = attributes.get("TEXCOORD_0");
		if(texcoordsAccessorModel != null) {
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createTexcoordMorphTarget(morphTargets, targetAccessorDatas)) {
				texcoordsAccessorModel = bindTexcoordMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, texcoordsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, texcoordsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaUV0,
					texcoordsAccessorModel.getElementType().getNumComponents(),
					texcoordsAccessorModel.getComponentType(),
					false,
					texcoordsAccessorModel.getByteStride(),
					texcoordsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaUV0);
		}
		
		ByteBuffer positionsBufferViewData = outputPositionsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer normalsBufferViewData = outputNormalsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer tangentsBufferViewData = outputTangentsAccessorModel.getBufferViewModel().getBufferViewData();
		
		int mode = meshPrimitiveModel.getMode();
		AccessorModel indices = meshPrimitiveModel.getIndices();
		if(indices != null) {
			int glIndicesBufferView = obtainElementArrayBuffer(gltfRenderData, indices.getBufferViewModel());
			int count = indices.getCount();
			int type = indices.getComponentType();
			int offset = indices.getByteOffset();
			renderCommand.add(() -> {
				skinningCommands.parallelStream().forEach(Runnable::run);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
				
				GL30.glBindVertexArray(glVertexArray);
				GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, glIndicesBufferView);
				GL11.glDrawElements(mode, count, type, offset);
			});
		}
		else {
			renderCommand.add(() -> {
				skinningCommands.parallelStream().forEach(Runnable::run);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
				GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
				
				GL30.glBindVertexArray(glVertexArray);
				GL11.glDrawArrays(mode, 0, pointCount);
			});
		}
	}
	
	protected void processMeshPrimitiveModelMikkTangent(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, List<Runnable> renderCommand, float[][] jointMatrices) {
		Pair<Map<String, AccessorModel>, List<Map<String, AccessorModel>>> unindexed = obtainUnindexed(meshPrimitiveModel);
		Map<String, AccessorModel> attributes = unindexed.getLeft();
		List<Map<String, AccessorModel>> morphTargets = unindexed.getRight();
		
		AccessorModel positionsAccessorModel = attributes.get("POSITION");
		AccessorModel outputPositionsAccessorModel;
		List<AccessorFloatData> targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "POSITION")) {
			outputPositionsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, positionsAccessorModel, targetAccessorDatas);
		}
		else {
			outputPositionsAccessorModel = AccessorModelCreation.instantiate(positionsAccessorModel, "");
		}
		
		AccessorModel normalsAccessorModel = attributes.get("NORMAL");
		AccessorModel outputNormalsAccessorModel;
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createMorphTarget(morphTargets, targetAccessorDatas, "NORMAL")) {
			outputNormalsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, normalsAccessorModel, targetAccessorDatas);
		}
		else {
			outputNormalsAccessorModel = AccessorModelCreation.instantiate(normalsAccessorModel, "");
		}
		
		AccessorModel texcoordsAccessorModel = attributes.get("TEXCOORD_0");
		AccessorModel outputTangentsAccessorModel;
		AccessorModel tangentsAccessorModel = obtainTangentsAccessorModel(meshPrimitiveModel, positionsAccessorModel, normalsAccessorModel, texcoordsAccessorModel);
		if(createMorphTarget(morphTargets, targetAccessorDatas, "TANGENT")) {
			outputTangentsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, tangentsAccessorModel, targetAccessorDatas);
		}
		else {
			outputTangentsAccessorModel = AccessorModelCreation.instantiate(tangentsAccessorModel, "");
		}
		
		int pointCount = positionsAccessorModel.getCount();
		List<Runnable> skinningCommands = createSoftwareSkinningCommands(pointCount, jointMatrices,
				AccessorDatas.createInt(obtainUnsignedJointsModel(attributes.get("JOINTS_0"))),
				AccessorDatas.createFloat(obtainDequantizedWeightsModel(attributes.get("WEIGHTS_0"))),
				AccessorDatas.createFloat(positionsAccessorModel),
				AccessorDatas.createFloat(normalsAccessorModel),
				AccessorDatas.createFloat(tangentsAccessorModel),
				AccessorDatas.createFloat(outputPositionsAccessorModel),
				AccessorDatas.createFloat(outputNormalsAccessorModel),
				AccessorDatas.createFloat(outputTangentsAccessorModel));
		
		int glVertexArray = GL30.glGenVertexArrays();
		gltfRenderData.add(() -> GL30.glDeleteVertexArrays(glVertexArray));
		GL30.glBindVertexArray(glVertexArray);
		
		int positionBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(positionBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputPositionsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaPosition,
				outputPositionsAccessorModel.getElementType().getNumComponents(),
				outputPositionsAccessorModel.getComponentType(),
				false,
				outputPositionsAccessorModel.getByteStride(),
				outputPositionsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaPosition);
		
		int normalBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(normalBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputNormalsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaNormal,
				outputNormalsAccessorModel.getElementType().getNumComponents(),
				outputNormalsAccessorModel.getComponentType(),
				false,
				outputNormalsAccessorModel.getByteStride(),
				outputNormalsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaNormal);
		
		int tangentBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(tangentBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputTangentsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				at_tangent,
				outputTangentsAccessorModel.getElementType().getNumComponents(),
				outputTangentsAccessorModel.getComponentType(),
				false,
				outputTangentsAccessorModel.getByteStride(),
				outputTangentsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(at_tangent);
		
		AccessorModel colorsAccessorModel = attributes.get("COLOR_0");
		if(colorsAccessorModel != null) {
			colorsAccessorModel = obtainVec4ColorsAccessorModel(colorsAccessorModel);
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createColorMorphTarget(morphTargets, targetAccessorDatas)) {
				colorsAccessorModel = bindColorMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, colorsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, colorsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaColor,
					colorsAccessorModel.getElementType().getNumComponents(),
					colorsAccessorModel.getComponentType(),
					false,
					colorsAccessorModel.getByteStride(),
					colorsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaColor);
		}
		
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createTexcoordMorphTarget(morphTargets, targetAccessorDatas)) {
			texcoordsAccessorModel = bindTexcoordMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, texcoordsAccessorModel, targetAccessorDatas);
		}
		else {
			bindArrayBufferViewModel(gltfRenderData, texcoordsAccessorModel.getBufferViewModel());
		}
		GL20.glVertexAttribPointer(
				vaUV0,
				texcoordsAccessorModel.getElementType().getNumComponents(),
				texcoordsAccessorModel.getComponentType(),
				false,
				texcoordsAccessorModel.getByteStride(),
				texcoordsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaUV0);
		
		ByteBuffer positionsBufferViewData = outputPositionsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer normalsBufferViewData = outputNormalsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer tangentsBufferViewData = outputTangentsAccessorModel.getBufferViewModel().getBufferViewData();
		
		int mode = meshPrimitiveModel.getMode();
		renderCommand.add(() -> {
			skinningCommands.parallelStream().forEach(Runnable::run);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
			
			GL30.glBindVertexArray(glVertexArray);
			GL11.glDrawArrays(mode, 0, pointCount);
		});
	}
	
	protected void processMeshPrimitiveModelFlatNormalSimpleTangent(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, List<Runnable> renderCommand, float[][] jointMatrices) {
		Pair<Map<String, AccessorModel>, List<Map<String, AccessorModel>>> unindexed = obtainUnindexed(meshPrimitiveModel);
		Map<String, AccessorModel> attributes = unindexed.getLeft();
		List<Map<String, AccessorModel>> morphTargets = unindexed.getRight();
		
		AccessorModel positionsAccessorModel = attributes.get("POSITION");
		AccessorModel normalsAccessorModel = obtainNormalsAccessorModel(positionsAccessorModel);
		AccessorModel tangentsAccessorModel = obtainTangentsAccessorModel(normalsAccessorModel);
		AccessorModel outputPositionsAccessorModel;
		AccessorModel outputNormalsAccessorModel;
		AccessorModel outputTangentsAccessorModel;
		List<AccessorFloatData> targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		List<AccessorFloatData> normalTargetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		List<AccessorFloatData> tangentTargetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createPositionNormalTangentMorphTarget(morphTargets, positionsAccessorModel, normalsAccessorModel, tangentsAccessorModel, targetAccessorDatas, normalTargetAccessorDatas, tangentTargetAccessorDatas)) {
			outputPositionsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, positionsAccessorModel, targetAccessorDatas);
			outputNormalsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, normalsAccessorModel, targetAccessorDatas);
			outputTangentsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, tangentsAccessorModel, targetAccessorDatas);
		}
		else {
			outputPositionsAccessorModel = AccessorModelCreation.instantiate(positionsAccessorModel, "");
			outputNormalsAccessorModel = AccessorModelCreation.instantiate(normalsAccessorModel, "");
			outputTangentsAccessorModel = AccessorModelCreation.instantiate(tangentsAccessorModel, "");
		}
		
		int pointCount = positionsAccessorModel.getCount();
		List<Runnable> skinningCommands = createSoftwareSkinningCommands(pointCount, jointMatrices,
				AccessorDatas.createInt(obtainUnsignedJointsModel(attributes.get("JOINTS_0"))),
				AccessorDatas.createFloat(obtainDequantizedWeightsModel(attributes.get("WEIGHTS_0"))),
				AccessorDatas.createFloat(positionsAccessorModel),
				AccessorDatas.createFloat(normalsAccessorModel),
				AccessorDatas.createFloat(tangentsAccessorModel),
				AccessorDatas.createFloat(outputPositionsAccessorModel),
				AccessorDatas.createFloat(outputNormalsAccessorModel),
				AccessorDatas.createFloat(outputTangentsAccessorModel));
		
		int glVertexArray = GL30.glGenVertexArrays();
		gltfRenderData.add(() -> GL30.glDeleteVertexArrays(glVertexArray));
		GL30.glBindVertexArray(glVertexArray);
		
		int positionBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(positionBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputPositionsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaPosition,
				outputPositionsAccessorModel.getElementType().getNumComponents(),
				outputPositionsAccessorModel.getComponentType(),
				false,
				outputPositionsAccessorModel.getByteStride(),
				outputPositionsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaPosition);
		
		int normalBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(normalBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputNormalsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaNormal,
				outputNormalsAccessorModel.getElementType().getNumComponents(),
				outputNormalsAccessorModel.getComponentType(),
				false,
				outputNormalsAccessorModel.getByteStride(),
				outputNormalsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaNormal);
		
		int tangentBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(tangentBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputTangentsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				at_tangent,
				outputTangentsAccessorModel.getElementType().getNumComponents(),
				outputTangentsAccessorModel.getComponentType(),
				false,
				outputTangentsAccessorModel.getByteStride(),
				outputTangentsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(at_tangent);
		
		AccessorModel colorsAccessorModel = attributes.get("COLOR_0");
		if(colorsAccessorModel != null) {
			colorsAccessorModel = obtainVec4ColorsAccessorModel(colorsAccessorModel);
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createColorMorphTarget(morphTargets, targetAccessorDatas)) {
				colorsAccessorModel = bindColorMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, colorsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, colorsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaColor,
					colorsAccessorModel.getElementType().getNumComponents(),
					colorsAccessorModel.getComponentType(),
					false,
					colorsAccessorModel.getByteStride(),
					colorsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaColor);
		}
		
		AccessorModel texcoordsAccessorModel = attributes.get("TEXCOORD_0");
		if(texcoordsAccessorModel != null) {
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createTexcoordMorphTarget(morphTargets, targetAccessorDatas)) {
				texcoordsAccessorModel = bindTexcoordMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, texcoordsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, texcoordsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaUV0,
					texcoordsAccessorModel.getElementType().getNumComponents(),
					texcoordsAccessorModel.getComponentType(),
					false,
					texcoordsAccessorModel.getByteStride(),
					texcoordsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaUV0);
		}
		
		ByteBuffer positionsBufferViewData = outputPositionsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer normalsBufferViewData = outputNormalsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer tangentsBufferViewData = outputTangentsAccessorModel.getBufferViewModel().getBufferViewData();
		
		int mode = meshPrimitiveModel.getMode();
		renderCommand.add(() -> {
			skinningCommands.parallelStream().forEach(Runnable::run);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
			
			GL30.glBindVertexArray(glVertexArray);
			GL11.glDrawArrays(mode, 0, pointCount);
		});
	}
	
	protected void processMeshPrimitiveModelFlatNormalMikkTangent(List<Runnable> gltfRenderData, NodeModel nodeModel, MeshModel meshModel, MeshPrimitiveModel meshPrimitiveModel, List<Runnable> renderCommand, float[][] jointMatrices) {
		Pair<Map<String, AccessorModel>, List<Map<String, AccessorModel>>> unindexed = obtainUnindexed(meshPrimitiveModel);
		Map<String, AccessorModel> attributes = unindexed.getLeft();
		List<Map<String, AccessorModel>> morphTargets = unindexed.getRight();
		
		AccessorModel positionsAccessorModel = attributes.get("POSITION");
		AccessorModel normalsAccessorModel = obtainNormalsAccessorModel(positionsAccessorModel);
		AccessorModel outputPositionsAccessorModel;
		AccessorModel outputNormalsAccessorModel;
		List<AccessorFloatData> targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		List<AccessorFloatData> normalTargetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createPositionNormalMorphTarget(morphTargets, positionsAccessorModel, normalsAccessorModel, targetAccessorDatas, normalTargetAccessorDatas)) {
			outputPositionsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, positionsAccessorModel, targetAccessorDatas);
			outputNormalsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, normalsAccessorModel, targetAccessorDatas);
		}
		else {
			outputPositionsAccessorModel = AccessorModelCreation.instantiate(positionsAccessorModel, "");
			outputNormalsAccessorModel = AccessorModelCreation.instantiate(normalsAccessorModel, "");
		}
		
		AccessorModel texcoordsAccessorModel = attributes.get("TEXCOORD_0");
		AccessorModel tangentsAccessorModel = obtainTangentsAccessorModel(normalsAccessorModel);
		AccessorModel outputTangentsAccessorModel;
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createTangentMorphTarget(morphTargets, targetAccessorDatas, positionsAccessorModel, normalsAccessorModel, texcoordsAccessorModel, tangentsAccessorModel, normalTargetAccessorDatas)) {
			outputTangentsAccessorModel = obtainVec3FloatMorphedModel(nodeModel, meshModel, renderCommand, tangentsAccessorModel, targetAccessorDatas);
		}
		else {
			outputTangentsAccessorModel = AccessorModelCreation.instantiate(tangentsAccessorModel, "");
		}
		
		int pointCount = positionsAccessorModel.getCount();
		List<Runnable> skinningCommands = createSoftwareSkinningCommands(pointCount, jointMatrices,
				AccessorDatas.createInt(obtainUnsignedJointsModel(attributes.get("JOINTS_0"))),
				AccessorDatas.createFloat(obtainDequantizedWeightsModel(attributes.get("WEIGHTS_0"))),
				AccessorDatas.createFloat(positionsAccessorModel),
				AccessorDatas.createFloat(normalsAccessorModel),
				AccessorDatas.createFloat(tangentsAccessorModel),
				AccessorDatas.createFloat(outputPositionsAccessorModel),
				AccessorDatas.createFloat(outputNormalsAccessorModel),
				AccessorDatas.createFloat(outputTangentsAccessorModel));
		
		int glVertexArray = GL30.glGenVertexArrays();
		gltfRenderData.add(() -> GL30.glDeleteVertexArrays(glVertexArray));
		GL30.glBindVertexArray(glVertexArray);
		
		int positionBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(positionBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputPositionsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaPosition,
				outputPositionsAccessorModel.getElementType().getNumComponents(),
				outputPositionsAccessorModel.getComponentType(),
				false,
				outputPositionsAccessorModel.getByteStride(),
				outputPositionsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaPosition);
		
		int normalBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(normalBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputNormalsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				vaNormal,
				outputNormalsAccessorModel.getElementType().getNumComponents(),
				outputNormalsAccessorModel.getComponentType(),
				false,
				outputNormalsAccessorModel.getByteStride(),
				outputNormalsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaNormal);
		
		int tangentBuffer = GL15.glGenBuffers();
		gltfRenderData.add(() -> GL15.glDeleteBuffers(tangentBuffer));
		GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
		GL15.glBufferData(GL15.GL_ARRAY_BUFFER, outputTangentsAccessorModel.getBufferViewModel().getByteLength(), GL15.GL_STATIC_DRAW);
		GL20.glVertexAttribPointer(
				at_tangent,
				outputTangentsAccessorModel.getElementType().getNumComponents(),
				outputTangentsAccessorModel.getComponentType(),
				false,
				outputTangentsAccessorModel.getByteStride(),
				outputTangentsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(at_tangent);
		
		AccessorModel colorsAccessorModel = attributes.get("COLOR_0");
		if(colorsAccessorModel != null) {
			colorsAccessorModel = obtainVec4ColorsAccessorModel(colorsAccessorModel);
			targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
			if(createColorMorphTarget(morphTargets, targetAccessorDatas)) {
				colorsAccessorModel = bindColorMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, colorsAccessorModel, targetAccessorDatas);
			}
			else {
				bindArrayBufferViewModel(gltfRenderData, colorsAccessorModel.getBufferViewModel());
			}
			GL20.glVertexAttribPointer(
					vaColor,
					colorsAccessorModel.getElementType().getNumComponents(),
					colorsAccessorModel.getComponentType(),
					false,
					colorsAccessorModel.getByteStride(),
					colorsAccessorModel.getByteOffset());
			GL20.glEnableVertexAttribArray(vaColor);
		}
		
		targetAccessorDatas = new ArrayList<AccessorFloatData>(morphTargets.size());
		if(createTexcoordMorphTarget(morphTargets, targetAccessorDatas)) {
			texcoordsAccessorModel = bindTexcoordMorphed(gltfRenderData, nodeModel, meshModel, renderCommand, texcoordsAccessorModel, targetAccessorDatas);
		}
		else {
			bindArrayBufferViewModel(gltfRenderData, texcoordsAccessorModel.getBufferViewModel());
		}
		GL20.glVertexAttribPointer(
				vaUV0,
				texcoordsAccessorModel.getElementType().getNumComponents(),
				texcoordsAccessorModel.getComponentType(),
				false,
				texcoordsAccessorModel.getByteStride(),
				texcoordsAccessorModel.getByteOffset());
		GL20.glEnableVertexAttribArray(vaUV0);
		
		ByteBuffer positionsBufferViewData = outputPositionsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer normalsBufferViewData = outputNormalsAccessorModel.getBufferViewModel().getBufferViewData();
		ByteBuffer tangentsBufferViewData = outputTangentsAccessorModel.getBufferViewModel().getBufferViewData();
		
		int mode = meshPrimitiveModel.getMode();
		renderCommand.add(() -> {
			skinningCommands.parallelStream().forEach(Runnable::run);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, positionBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, positionsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, normalBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, normalsBufferViewData);
			
			GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, tangentBuffer);
			GL15.glBufferSubData(GL15.GL_ARRAY_BUFFER, 0, tangentsBufferViewData);
			
			GL30.glBindVertexArray(glVertexArray);
			GL11.glDrawArrays(mode, 0, pointCount);
		});
	}
	
	public AccessorModel obtainUnsignedJointsModel(AccessorModel accessorModel) {
		AccessorModel unsignedAccessorModel = jointsAccessorModelUnsignedLookup.get(accessorModel);
		if(unsignedAccessorModel == null) {
			int count = accessorModel.getCount();
			unsignedAccessorModel = AccessorModelCreation.createAccessorModel(GL11.GL_INT, count, ElementType.VEC4, "");
			AccessorIntData unsignedAccessorData = AccessorDatas.createInt(unsignedAccessorModel);
			if(accessorModel.getComponentDataType() == short.class) {
				AccessorShortData accessorData = AccessorDatas.createShort(accessorModel);
				for(int i = 0; i < count; i++) {
					unsignedAccessorData.set(i, 0, Short.toUnsignedInt(accessorData.get(i, 0)));
					unsignedAccessorData.set(i, 1, Short.toUnsignedInt(accessorData.get(i, 1)));
					unsignedAccessorData.set(i, 2, Short.toUnsignedInt(accessorData.get(i, 2)));
					unsignedAccessorData.set(i, 3, Short.toUnsignedInt(accessorData.get(i, 3)));
				}
			}
			else {
				AccessorByteData accessorData = AccessorDatas.createByte(accessorModel);
				for(int i = 0; i < count; i++) {
					unsignedAccessorData.set(i, 0, Byte.toUnsignedInt(accessorData.get(i, 0)));
					unsignedAccessorData.set(i, 1, Byte.toUnsignedInt(accessorData.get(i, 1)));
					unsignedAccessorData.set(i, 2, Byte.toUnsignedInt(accessorData.get(i, 2)));
					unsignedAccessorData.set(i, 3, Byte.toUnsignedInt(accessorData.get(i, 3)));
				}
			}
			jointsAccessorModelUnsignedLookup.put(accessorModel, unsignedAccessorModel);
		}
		return unsignedAccessorModel;
	}
	
	public AccessorModel obtainDequantizedWeightsModel(AccessorModel accessorModel) {
		AccessorModel dequantizedAccessorModel = weightsAccessorModelDequantizedLookup.get(accessorModel);
		if(dequantizedAccessorModel == null) {
			if(accessorModel.getComponentDataType() != float.class) {
				AccessorData accessorData = AccessorDatas.create(accessorModel);
				int count = accessorModel.getCount();
				dequantizedAccessorModel = AccessorModelCreation.createAccessorModel(GL11.GL_FLOAT, count, ElementType.VEC4, "");
				AccessorFloatData dequantizedAccessorData = AccessorDatas.createFloat(dequantizedAccessorModel);
				for(int i = 0; i < count; i++) {
					dequantizedAccessorData.set(i, 0, accessorData.getFloat(i, 0));
					dequantizedAccessorData.set(i, 1, accessorData.getFloat(i, 1));
					dequantizedAccessorData.set(i, 2, accessorData.getFloat(i, 2));
					dequantizedAccessorData.set(i, 3, accessorData.getFloat(i, 3));
				}
				weightsAccessorModelDequantizedLookup.put(accessorModel, dequantizedAccessorModel);
			}
			else {
				return accessorModel;
			}
		}
		return dequantizedAccessorModel;
	}
	
	public AccessorModel obtainVec3FloatMorphedModel(NodeModel nodeModel, MeshModel meshModel, List<Runnable> command, AccessorModel baseAccessorModel, List<AccessorFloatData> targetAccessorDatas) {
		AccessorModel morphedAccessorModel = AccessorModelCreation.instantiate(baseAccessorModel, "");
		AccessorFloatData baseAccessorData = AccessorDatas.createFloat(baseAccessorModel);
		AccessorFloatData morphedAccessorData = AccessorDatas.createFloat(morphedAccessorModel);
		
		float weights[] = new float[targetAccessorDatas.size()];
		int numComponents = 3;
		int numElements = morphedAccessorData.getNumElements();
		
		List<Runnable> morphingCommands = new ArrayList<Runnable>(numElements * numComponents);
		for(int element = 0; element < numElements; element++) {
			for(int component = 0; component < numComponents; component++) {
				int e = element;
				int c = component;
				morphingCommands.add(() -> {
					float r = baseAccessorData.get(e, c);
					for(int i = 0; i < weights.length; i++) {
						AccessorFloatData target = targetAccessorDatas.get(i);
						if(target != null) {
							r += weights[i] * target.get(e, c);
						}
					}
					morphedAccessorData.set(e, c, r);
				});
			}
		}
		
		command.add(() -> {
			if(nodeModel.getWeights() != null) System.arraycopy(nodeModel.getWeights(), 0, weights, 0, weights.length);
			else if(meshModel.getWeights() != null) System.arraycopy(meshModel.getWeights(), 0, weights, 0, weights.length);
			
			morphingCommands.parallelStream().forEach(Runnable::run);
		});
		return morphedAccessorModel;
	}
	
	public List<Runnable> createSoftwareSkinningCommands(int pointCount, float[][] jointMatrices, AccessorIntData jointsAccessorData, AccessorFloatData weightsAccessorData, AccessorFloatData inputPositionsAccessorData, AccessorFloatData inputNormalsAccessorData, AccessorFloatData inputTangentsAccessorData, AccessorFloatData outputPositionsAccessorData, AccessorFloatData outputNormalsAccessorData, AccessorFloatData outputTangentsAccessorData) {
		List<Runnable> commands = new ArrayList<Runnable>(pointCount);
		for(int point = 0; point < pointCount; point++) {
			int p = point;
			commands.add(() -> {
				float wx = weightsAccessorData.get(p, 0);
				float wy = weightsAccessorData.get(p, 1);
				float wz = weightsAccessorData.get(p, 2);
				float ww = weightsAccessorData.get(p, 3);
				
				float[] jmx = jointMatrices[jointsAccessorData.get(p, 0)];
				float[] jmy = jointMatrices[jointsAccessorData.get(p, 1)];
				float[] jmz = jointMatrices[jointsAccessorData.get(p, 2)];
				float[] jmw = jointMatrices[jointsAccessorData.get(p, 3)];
				
				float sm00 = wx * jmx[ 0] + wy * jmy[ 0] + wz * jmz[ 0] + ww * jmw[ 0];
				float sm01 = wx * jmx[ 1] + wy * jmy[ 1] + wz * jmz[ 1] + ww * jmw[ 1];
				float sm02 = wx * jmx[ 2] + wy * jmy[ 2] + wz * jmz[ 2] + ww * jmw[ 2];
				float sm03 = wx * jmx[ 3] + wy * jmy[ 3] + wz * jmz[ 3] + ww * jmw[ 3];
				float sm10 = wx * jmx[ 4] + wy * jmy[ 4] + wz * jmz[ 4] + ww * jmw[ 4];
				float sm11 = wx * jmx[ 5] + wy * jmy[ 5] + wz * jmz[ 5] + ww * jmw[ 5];
				float sm12 = wx * jmx[ 6] + wy * jmy[ 6] + wz * jmz[ 6] + ww * jmw[ 6];
				float sm13 = wx * jmx[ 7] + wy * jmy[ 7] + wz * jmz[ 7] + ww * jmw[ 7];
				float sm20 = wx * jmx[ 8] + wy * jmy[ 8] + wz * jmz[ 8] + ww * jmw[ 8];
				float sm21 = wx * jmx[ 9] + wy * jmy[ 9] + wz * jmz[ 9] + ww * jmw[ 9];
				float sm22 = wx * jmx[10] + wy * jmy[10] + wz * jmz[10] + ww * jmw[10];
				float sm23 = wx * jmx[11] + wy * jmy[11] + wz * jmz[11] + ww * jmw[11];
				
				float px = inputPositionsAccessorData.get(p, 0);
				float py = inputPositionsAccessorData.get(p, 1);
				float pz = inputPositionsAccessorData.get(p, 2);
				
				outputPositionsAccessorData.set(p, 0, sm00 * px + sm01 * py + sm02 * pz + sm03);
				outputPositionsAccessorData.set(p, 1, sm10 * px + sm11 * py + sm12 * pz + sm13);
				outputPositionsAccessorData.set(p, 2, sm20 * px + sm21 * py + sm22 * pz + sm23);
				
				float nx = inputNormalsAccessorData.get(p, 0);
				float ny = inputNormalsAccessorData.get(p, 1);
				float nz = inputNormalsAccessorData.get(p, 2);
				
				outputNormalsAccessorData.set(p, 0, sm00 * nx + sm01 * ny + sm02 * nz);
				outputNormalsAccessorData.set(p, 1, sm10 * nx + sm11 * ny + sm12 * nz);
				outputNormalsAccessorData.set(p, 2, sm20 * nx + sm21 * ny + sm22 * nz);
				
				float tx = inputTangentsAccessorData.get(p, 0);
				float ty = inputTangentsAccessorData.get(p, 1);
				float tz = inputTangentsAccessorData.get(p, 2);
				
				outputTangentsAccessorData.set(p, 0, sm00 * tx + sm01 * ty + sm02 * tz);
				outputTangentsAccessorData.set(p, 1, sm10 * tx + sm11 * ty + sm12 * tz);
				outputTangentsAccessorData.set(p, 2, sm20 * tx + sm21 * ty + sm22 * tz);
			});
		}
		return commands;
	}
}

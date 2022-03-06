package com.timlee9024.mcgltf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import de.javagl.jgltf.model.MaterialModel;
import de.javagl.jgltf.model.io.Buffers;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.network.FMLNetworkConstants;

@Mod(MCglTF.MODID)
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MCglTF {

	public static final String MODID = "mcgltf";
	public static final String RESOURCE_LOCATION = "resourceLocation";
	public static final String MATERIAL_HANDLER = "materialHandler";
	
	public static final Logger logger = LogManager.getLogger(MODID);
	
	private static MCglTF INSTANCE;
	
	private int glProgramSkinnig;
	private int defaultColorMap;
	private int defaultNormalMap;
	
	private final Map<ResourceLocation, ByteBuffer> loadedBufferResources = new HashMap<ResourceLocation, ByteBuffer>();
	private final Map<ResourceLocation, ByteBuffer> loadedImageResources = new HashMap<ResourceLocation, ByteBuffer>();
	private final Map<ResourceLocation, RenderedGltfModel> renderedGltfModels = new HashMap<ResourceLocation, RenderedGltfModel>();
	private final List<IGltfModelReceiver> gltfModelReceivers = new ArrayList<IGltfModelReceiver>();
	private final List<GltfRenderData> gltfRenderDatas = new ArrayList<GltfRenderData>();
	private final Map<ResourceLocation, BiFunction<RenderedGltfModel, MaterialModel, IMaterialHandler>> materialHandlerFactories = new HashMap<ResourceLocation, BiFunction<RenderedGltfModel, MaterialModel, IMaterialHandler>>();
	
	public MCglTF() {
		INSTANCE = this;
		//Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
		ModLoadingContext.get().registerExtensionPoint(ExtensionPoint.DISPLAYTEST, () -> Pair.of(() -> FMLNetworkConstants.IGNORESERVERONLY, (a, b) -> true));
	}
	
	@SubscribeEvent
	public static void onEvent(final FMLClientSetupEvent event) {
		event.getMinecraftSupplier().get().execute(() -> {
			int glShader = GL20.glCreateShader(GL20.GL_VERTEX_SHADER);
			GL20.glShaderSource(glShader,
					  "#version 430\r\n"
					+ "layout(location = 0) in vec4 joint;"
					+ "layout(location = 1) in vec4 weight;"
					+ "layout(location = 2) in vec3 position;"
					+ "layout(location = 3) in vec3 normal;"
					+ "layout(location = 4) in vec4 tangent;"
					+ "layout(std430, binding = 0) readonly buffer jointMatrixBuffer {mat4 jointMatrix[];};"
					+ "out vec3 outPosition;"
					+ "out vec3 outNormal;"
					+ "out vec4 outTangent;"
					+ "void main() {"
					+ "mat4 skinMatrix ="
					+ " weight.x * jointMatrix[int(joint.x)] +"
					+ " weight.y * jointMatrix[int(joint.y)] +"
					+ " weight.z * jointMatrix[int(joint.z)] +"
					+ " weight.w * jointMatrix[int(joint.w)];"
					+ "outPosition = (skinMatrix * vec4(position, 1.0)).xyz;"
					+ "mat3 upperLeft = mat3(skinMatrix);"
					+ "outNormal = upperLeft * normal;"
					+ "outTangent.xyz = upperLeft * tangent.xyz;"
					+ "outTangent.w = tangent.w;"
					+ "}");
			GL20.glCompileShader(glShader);
			
			INSTANCE.glProgramSkinnig = GL20.glCreateProgram();
			GL20.glAttachShader(INSTANCE.glProgramSkinnig, glShader);
			GL20.glDeleteShader(glShader);
			GL30.glTransformFeedbackVaryings(INSTANCE.glProgramSkinnig, new CharSequence[]{"outPosition", "outNormal", "outTangent"}, GL30.GL_SEPARATE_ATTRIBS);
			GL20.glLinkProgram(INSTANCE.glProgramSkinnig);
			
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			
			GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
			
			INSTANCE.defaultColorMap = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, INSTANCE.defaultColorMap);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, Buffers.create(new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}));
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
			
			INSTANCE.defaultNormalMap = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, INSTANCE.defaultNormalMap);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, Buffers.create(new byte[]{-128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1}));
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
			
			GL11.glPopAttrib();
		});
	}
	
	@SubscribeEvent
	public static void onEvent(final ModelBakeEvent event) {
		INSTANCE.gltfRenderDatas.forEach((gltfRenderData) -> gltfRenderData.delete());
		INSTANCE.gltfRenderDatas.clear();
		
		GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
		GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
		
		GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
		INSTANCE.gltfModelReceivers.forEach((receiver) -> {
			try {
				ResourceLocation modelLocation = receiver.getModelLocation();
				RenderedGltfModel renderedModel = INSTANCE.renderedGltfModels.get(modelLocation);
				if(renderedModel == null) {
					renderedModel = new RenderedGltfModel(new GltfModelReader().readWithoutReferences(Minecraft.getInstance().getResourceManager().getResource(modelLocation).getInputStream()));
					INSTANCE.renderedGltfModels.put(modelLocation, renderedModel);
					INSTANCE.gltfRenderDatas.add(renderedModel.gltfRenderData);
				}
				receiver.onModelLoaded(renderedModel);
			} catch (Exception e) {
				e.printStackTrace();
			}
		});
		GL11.glPopAttrib();
		
		INSTANCE.renderedGltfModels.clear();
		INSTANCE.loadedBufferResources.clear();
		INSTANCE.loadedImageResources.clear();
	}
	
	public int getGlProgramSkinnig() {
		return glProgramSkinnig;
	}
	
	public int getDefaultColorMap() {
		return defaultColorMap;
	}
	
	public int getDefaultNormalMap() {
		return defaultNormalMap;
	}
	
	public int getDefaultSpecularMap() {
		return 0;
	}
	
	public ByteBuffer getBufferResource(ResourceLocation location) {
		ByteBuffer bufferData = loadedBufferResources.get(location);
		if(bufferData == null) {
			try {
				bufferData = Buffers.create(IOUtils.toByteArray(Minecraft.getInstance().getResourceManager().getResource(location).getInputStream()));
				loadedBufferResources.put(location, bufferData);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return bufferData;
	}
	
	public ByteBuffer getImageResource(ResourceLocation location) {
		ByteBuffer bufferData = loadedImageResources.get(location);
		if(bufferData == null) {
			try {
				bufferData = Buffers.create(IOUtils.toByteArray(Minecraft.getInstance().getResourceManager().getResource(location).getInputStream()));
				loadedImageResources.put(location, bufferData);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return bufferData;
	}
	
	public synchronized void addGltfModelReceiver(IGltfModelReceiver receiver) {
		gltfModelReceivers.add(receiver);
	}
	
	public synchronized boolean removeGltfModelReceiver(IGltfModelReceiver receiver) {
		return gltfModelReceivers.remove(receiver);
	}
	
	public synchronized void registerMaterialHandlerFactory(ResourceLocation location, BiFunction<RenderedGltfModel, MaterialModel, IMaterialHandler> materialHandlerFactory) {
		materialHandlerFactories.put(location, materialHandlerFactory);
	}
	
	public synchronized BiFunction<RenderedGltfModel, MaterialModel, IMaterialHandler> getMaterialHandlerFactory(ResourceLocation location) {
		return materialHandlerFactories.get(location);
	}
	
	public static MCglTF getInstance() {
		return INSTANCE;
	}

}

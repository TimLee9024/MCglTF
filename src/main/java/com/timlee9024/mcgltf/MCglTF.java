package com.timlee9024.mcgltf;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL40;
import org.lwjgl.opengl.GL43;

import de.javagl.jgltf.model.GltfModel;
import de.javagl.jgltf.model.io.Buffers;
import de.javagl.jgltf.model.io.GltfModelReader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

@Mod(MCglTF.MODID)
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public class MCglTF {

	public static final String MODID = "mcgltf";
	public static final String RESOURCE_LOCATION = "resourceLocation";
	
	public static final Logger logger = LogManager.getLogger(MODID);
	
	private static MCglTF INSTANCE;
	
	private int glProgramSkinnig;
	private int defaultColorMap;
	private int defaultNormalMap;
	
	private AbstractTexture lightTexture;
	
	private final Map<ResourceLocation, Supplier<ByteBuffer>> loadedBufferResources = new HashMap<ResourceLocation, Supplier<ByteBuffer>>();
	private final Map<ResourceLocation, Supplier<ByteBuffer>> loadedImageResources = new HashMap<ResourceLocation, Supplier<ByteBuffer>>();
	private final Map<ResourceLocation, RenderedGltfModel> renderedGltfModels = new HashMap<ResourceLocation, RenderedGltfModel>();
	private final List<IGltfModelReceiver> gltfModelReceivers = new ArrayList<IGltfModelReceiver>();
	private final List<GltfRenderData> gltfRenderDatas = new ArrayList<GltfRenderData>();
	
	private final boolean isOptiFineExist;
	
	public MCglTF() {
		INSTANCE = this;
		//Make sure the mod being absent on the other network side does not cause the client to display the server as incompatible
		ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (a, b) -> true));
		
		Class<?> clazz = null;
		try {
			clazz = Class.forName("net.optifine.shaders.Shaders");
		} catch (ClassNotFoundException e) {
			//Hush
		}
		isOptiFineExist = clazz != null;
	}
	
	@SubscribeEvent
	public static void onEvent(RegisterClientReloadListenersEvent event) {
		INSTANCE.lightTexture = Minecraft.getInstance().getTextureManager().getTexture(new ResourceLocation("dynamic/light_map_1"));
		
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
		
		int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
		
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
		
		GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
		
		event.registerReloadListener(new ResourceManagerReloadListener() {

			@Override
			public void onResourceManagerReload(ResourceManager p_10758_) {
				INSTANCE.gltfRenderDatas.forEach((gltfRenderData) -> gltfRenderData.delete());
				INSTANCE.gltfRenderDatas.clear();
				
				GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
				
				int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				
				Map<ResourceLocation, MutablePair<GltfModel, List<IGltfModelReceiver>>> lookup = new HashMap<ResourceLocation, MutablePair<GltfModel, List<IGltfModelReceiver>>>();
				INSTANCE.gltfModelReceivers.forEach((receiver) -> {
					ResourceLocation modelLocation = receiver.getModelLocation();
					MutablePair<GltfModel, List<IGltfModelReceiver>> receivers = lookup.get(modelLocation);
					if(receivers == null) {
						receivers = MutablePair.of(null, new ArrayList<IGltfModelReceiver>());
						lookup.put(modelLocation, receivers);
					}
					receivers.getRight().add(receiver);
				});
				ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
				lookup.entrySet().parallelStream().forEach((entry) -> {
					Thread.currentThread().setContextClassLoader(currentClassLoader); //Prevent ClassNotFoundException from Forge EventSubclassTransformer
					try {
						entry.getValue().setLeft(new GltfModelReader().readWithoutReferences(new BufferedInputStream(Minecraft.getInstance().getResourceManager().getResource(entry.getKey()).orElseThrow().open())));
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				lookup.forEach((modelLocation, receivers) -> {
					Iterator<IGltfModelReceiver> iterator = receivers.getRight().iterator();
					do {
						IGltfModelReceiver receiver = iterator.next();
						if(receiver.isReceiveSharedModel(receivers.getLeft(), INSTANCE.gltfRenderDatas)) {
							RenderedGltfModel renderedModel = new RenderedGltfModel(receivers.getLeft());
							INSTANCE.gltfRenderDatas.add(renderedModel.gltfRenderData);
							receiver.onReceiveSharedModel(renderedModel);
							while(iterator.hasNext()) {
								receiver = iterator.next();
								if(receiver.isReceiveSharedModel(receivers.getLeft(), INSTANCE.gltfRenderDatas)) {
									receiver.onReceiveSharedModel(renderedModel);
								}
							}
							return;
						}
					}
					while(iterator.hasNext());
				});
				
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
				
				GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
				GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
				GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0);
				GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
				GL30.glBindVertexArray(0);
				GL40.glBindTransformFeedback(GL40.GL_TRANSFORM_FEEDBACK, 0);
				INSTANCE.renderedGltfModels.clear();
				INSTANCE.loadedBufferResources.clear();
				INSTANCE.loadedImageResources.clear();
			}
			
		});
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
	
	public AbstractTexture getLightTexture() {
		return lightTexture;
	}
	
	public ByteBuffer getBufferResource(ResourceLocation location) {
		Supplier<ByteBuffer> supplier;
		synchronized(loadedBufferResources) {
			supplier = loadedBufferResources.get(location);
			if(supplier == null) {
				supplier = new Supplier<ByteBuffer>() {
					ByteBuffer bufferData;
					
					@Override
					public synchronized ByteBuffer get() {
						if(bufferData == null) {
							try {
								bufferData = Buffers.create(IOUtils.toByteArray(new BufferedInputStream(Minecraft.getInstance().getResourceManager().getResource(location).orElseThrow().open())));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						return bufferData;
					}
					
				};
				loadedBufferResources.put(location, supplier);
			}
		}
		return supplier.get();
	}
	
	public ByteBuffer getImageResource(ResourceLocation location) {
		Supplier<ByteBuffer> supplier;
		synchronized(loadedImageResources) {
			supplier = loadedImageResources.get(location);
			if(supplier == null) {
				supplier = new Supplier<ByteBuffer>() {
					ByteBuffer bufferData;
					
					@Override
					public synchronized ByteBuffer get() {
						if(bufferData == null) {
							try {
								bufferData = Buffers.create(IOUtils.toByteArray(new BufferedInputStream(Minecraft.getInstance().getResourceManager().getResource(location).orElseThrow().open())));
							} catch (IOException e) {
								e.printStackTrace();
							}
						}
						return bufferData;
					}
					
				};
				loadedImageResources.put(location, supplier);
			}
		}
		return supplier.get();
	}
	
	public synchronized void addGltfModelReceiver(IGltfModelReceiver receiver) {
		gltfModelReceivers.add(receiver);
	}
	
	public synchronized boolean removeGltfModelReceiver(IGltfModelReceiver receiver) {
		return gltfModelReceivers.remove(receiver);
	}
	
	public boolean isShaderModActive() {
		return isOptiFineExist && net.optifine.shaders.Shaders.isShaderPackInitialized && !net.optifine.shaders.Shaders.currentShaderName.equals(net.optifine.shaders.Shaders.SHADER_PACK_NAME_DEFAULT);
	}
	
	public static MCglTF getInstance() {
		return INSTANCE;
	}

}

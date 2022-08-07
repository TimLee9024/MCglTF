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
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

public class MCglTF implements ModInitializer {

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
	
	private boolean isOptiFineExist;
	
	@Override
	public void onInitialize() {
		INSTANCE = this;
		
		Minecraft.getInstance().execute(() -> {
			lightTexture = Minecraft.getInstance().getTextureManager().getTexture(new ResourceLocation("dynamic/light_map_1"));
			
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
			
			glProgramSkinnig = GL20.glCreateProgram();
			GL20.glAttachShader(glProgramSkinnig, glShader);
			GL20.glDeleteShader(glShader);
			GL30.glTransformFeedbackVaryings(glProgramSkinnig, new CharSequence[]{"outPosition", "outNormal", "outTangent"}, GL30.GL_SEPARATE_ATTRIBS);
			GL20.glLinkProgram(glProgramSkinnig);
			
			GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
			GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
			
			int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
			
			defaultColorMap = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultColorMap);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, Buffers.create(new byte[]{-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1}));
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
			
			defaultNormalMap = GL11.glGenTextures();
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, defaultNormalMap);
			GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, 2, 2, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, Buffers.create(new byte[]{-128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1, -128, -128, -1, -1}));
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_BASE_LEVEL, 0);
			GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
			
			GL11.glBindTexture(GL11.GL_TEXTURE_2D, currentTexture);
		});
		
		isOptiFineExist = FabricLoader.getInstance().isModLoaded("optifabric");
		
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {

			@Override
			public ResourceLocation getFabricId() {
				return new ResourceLocation(MODID, "gltf_reload_listener");
			}

			@Override
			public void onResourceManagerReload(ResourceManager resourceManager) {
				gltfRenderDatas.forEach((gltfRenderData) -> gltfRenderData.delete());
				gltfRenderDatas.clear();
				
				GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
				GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 4);
				
				int currentTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
				
				Map<ResourceLocation, MutablePair<GltfModel, List<IGltfModelReceiver>>> lookup = new HashMap<ResourceLocation, MutablePair<GltfModel, List<IGltfModelReceiver>>>();
				gltfModelReceivers.forEach((receiver) -> {
					ResourceLocation modelLocation = receiver.getModelLocation();
					MutablePair<GltfModel, List<IGltfModelReceiver>> receivers = lookup.get(modelLocation);
					if(receivers == null) {
						receivers = MutablePair.of(null, new ArrayList<IGltfModelReceiver>());
						lookup.put(modelLocation, receivers);
					}
					receivers.getRight().add(receiver);
				});
				lookup.entrySet().parallelStream().forEach((entry) -> {
					try(Resource resource = Minecraft.getInstance().getResourceManager().getResource(entry.getKey())) {
						entry.getValue().setLeft(new GltfModelReader().readWithoutReferences(new BufferedInputStream(resource.getInputStream())));
					} catch (IOException e) {
						e.printStackTrace();
					}
				});
				lookup.forEach((modelLocation, receivers) -> {
					Iterator<IGltfModelReceiver> iterator = receivers.getRight().iterator();
					do {
						IGltfModelReceiver receiver = iterator.next();
						if(receiver.isReceiveSharedModel(receivers.getLeft(), gltfRenderDatas)) {
							RenderedGltfModel renderedModel = new RenderedGltfModel(receivers.getLeft());
							gltfRenderDatas.add(renderedModel.gltfRenderData);
							receiver.onReceiveSharedModel(renderedModel);
							while(iterator.hasNext()) {
								receiver = iterator.next();
								if(receiver.isReceiveSharedModel(receivers.getLeft(), gltfRenderDatas)) {
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
				renderedGltfModels.clear();
				loadedBufferResources.clear();
				loadedImageResources.clear();
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
							try(Resource resource = Minecraft.getInstance().getResourceManager().getResource(location)) {
								bufferData = Buffers.create(IOUtils.toByteArray(new BufferedInputStream(resource.getInputStream())));
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
							try(Resource resource = Minecraft.getInstance().getResourceManager().getResource(location)) {
								bufferData = Buffers.create(IOUtils.toByteArray(new BufferedInputStream(resource.getInputStream())));
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

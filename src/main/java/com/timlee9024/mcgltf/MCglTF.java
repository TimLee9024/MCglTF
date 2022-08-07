package com.timlee9024.mcgltf;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
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
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.client.resources.SimpleReloadableResourceManager;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.resource.IResourceType;
import net.minecraftforge.client.resource.ISelectiveResourceReloadListener;
import net.minecraftforge.client.resource.VanillaResourceType;
import net.minecraftforge.fml.client.FMLClientHandler;
import net.minecraftforge.fml.client.SplashProgress;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.event.FMLLoadCompleteEvent;

@Mod(modid = MCglTF.MODID, clientSideOnly = true, useMetadata = true)
public class MCglTF {

	public static final String MODID = "mcgltf";
	public static final String RESOURCE_LOCATION = "resourceLocation";
	
	public static final Logger logger = LogManager.getLogger(MODID);
	
	private static MCglTF INSTANCE;
	
	private final int glProgramSkinnig;
	private final int defaultColorMap;
	private final int defaultNormalMap;
	
	private final Map<ResourceLocation, Supplier<ByteBuffer>> loadedBufferResources = new HashMap<ResourceLocation, Supplier<ByteBuffer>>();
	private final Map<ResourceLocation, Supplier<ByteBuffer>> loadedImageResources = new HashMap<ResourceLocation, Supplier<ByteBuffer>>();
	private final List<IGltfModelReceiver> gltfModelReceivers = new ArrayList<IGltfModelReceiver>();
	private final List<GltfRenderData> gltfRenderDatas = new ArrayList<GltfRenderData>();
	
	public MCglTF() {
		INSTANCE = this;
		
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
		
		GL11.glPushAttrib(GL11.GL_TEXTURE_BIT);
		
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
		
		GL11.glPopAttrib();
	}
	
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onEvent(FMLLoadCompleteEvent event) {
		SplashProgress.pause(); //This prevent the container object generated by glGenVertexArrays and glGenTransformFeedbacks during the game startup become invalid after SplashProgress#finish().
		
		((SimpleReloadableResourceManager)Minecraft.getMinecraft().getResourceManager()).registerReloadListener(new ISelectiveResourceReloadListener() {

			@Override
			public void onResourceManagerReload(IResourceManager resourceManager, Predicate<IResourceType> resourcePredicate) {
				if(resourcePredicate.test(VanillaResourceType.MODELS)) {
					gltfRenderDatas.forEach(GltfRenderData::delete);
					gltfRenderDatas.clear();
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
						try(IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(entry.getKey())) {
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
					
					GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
					GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
					GL15.glBindBuffer(GL30.GL_TRANSFORM_FEEDBACK_BUFFER, 0);
					GL15.glBindBuffer(GL43.GL_SHADER_STORAGE_BUFFER, 0);
					GL30.glBindVertexArray(0);
					GL40.glBindTransformFeedback(GL40.GL_TRANSFORM_FEEDBACK, 0);
					loadedBufferResources.clear();
					loadedImageResources.clear();
				}
			}
			
		});
		
		SplashProgress.resume();
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
		Supplier<ByteBuffer> supplier;
		synchronized(loadedBufferResources) {
			supplier = loadedBufferResources.get(location);
			if(supplier == null) {
				supplier = new Supplier<ByteBuffer>() {
					ByteBuffer bufferData;
					
					@Override
					public synchronized ByteBuffer get() {
						if(bufferData == null) {
							try(IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
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
							try(IResource resource = Minecraft.getMinecraft().getResourceManager().getResource(location)) {
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
	
	public void addGltfModelReceiver(IGltfModelReceiver receiver) {
		gltfModelReceivers.add(receiver);
	}
	
	public boolean removeGltfModelReceiver(IGltfModelReceiver receiver) {
		return gltfModelReceivers.remove(receiver);
	}
	
	public boolean isShaderModActive() {
		return FMLClientHandler.instance().hasOptifine() && net.optifine.shaders.Shaders.isShaderPackInitialized;
	}
	
	public static MCglTF getInstance() {
		return INSTANCE;
	}

}

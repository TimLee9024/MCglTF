package com.modularmods.mcgltf.iris;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import com.modularmods.mcgltf.RenderedGltfModel;

import net.coderbot.batchedentityrendering.impl.WrappableRenderType;
import net.coderbot.iris.Iris;
import net.coderbot.iris.pipeline.WorldRenderingPhase;
import net.coderbot.iris.pipeline.WorldRenderingPipeline;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;

public class IrisRenderingHook {

	private static final Map<WorldRenderingPhase, Map<RenderStateShard, List<Runnable>>> phaseRenderStateShardToCommands = new EnumMap<WorldRenderingPhase, Map<RenderStateShard, List<Runnable>>>(WorldRenderingPhase.class);
	private static RenderStateShard currentRenderStateShard;
	
	public static void submitCommandForIrisRenderingByPhaseName(String phase, RenderType renderType, Runnable command) {
		submitCommandForIrisRendering(WorldRenderingPhase.valueOf(phase), renderType, command);
	}
	
	public static void submitCommandForIrisRendering(WorldRenderingPhase phase, RenderType renderType, Runnable command) {
		Map<RenderStateShard, List<Runnable>> renderStateShardToCommands = phaseRenderStateShardToCommands.get(phase);
		if(renderStateShardToCommands == null) {
			renderStateShardToCommands = new LinkedHashMap<RenderStateShard, List<Runnable>>();
			phaseRenderStateShardToCommands.put(phase, renderStateShardToCommands);
			List<Runnable> commands = new LinkedList<Runnable>();
			renderStateShardToCommands.put(renderType, commands);
			commands.add(command);
		}
		else {
			List<Runnable> commands = renderStateShardToCommands.get(renderType);
			if(commands == null) {
				commands = new LinkedList<Runnable>();
				renderStateShardToCommands.put(renderType, commands);
			}
			commands.add(command);
		}
	}
	
	public static void irisHookAfterSetupRenderState(RenderStateShard renderStateShard) {
		currentRenderStateShard = ((renderStateShard instanceof WrappableRenderType) ? ((WrappableRenderType)renderStateShard).unwrap() : renderStateShard);
	}
	
	public static void irisHookBypassBufferUploaderVextexCountCheck() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
		if(pipeline != null) {
			WorldRenderingPhase phase = pipeline.getPhase();
			Map<RenderStateShard, List<Runnable>> renderStateShardToCommands = phaseRenderStateShardToCommands.get(phase);
			if(renderStateShardToCommands != null) {
				List<Runnable> commands = renderStateShardToCommands.remove(currentRenderStateShard);
				if(commands != null) {
					pipeline.syncProgram();
					if(phase != WorldRenderingPhase.NONE) {
						int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
						int normals = GL20.glGetUniformLocation(currentProgram, "normals");
						int specular = GL20.glGetUniformLocation(currentProgram, "specular");
						RenderedGltfModel.NORMAL_MAP_INDEX = ((normals == -1) ? -1 : GL13.GL_TEXTURE0 + GL20.glGetUniformi(currentProgram, normals));
						RenderedGltfModel.SPECULAR_MAP_INDEX = ((specular == -1) ? -1 : GL13.GL_TEXTURE0 + GL20.glGetUniformi(currentProgram, specular));
					}
					commands.forEach(Runnable::run);
				}
			}
		}
	}
	
	public static void irisHookAfterDrawArrays() {
		WorldRenderingPipeline pipeline = Iris.getPipelineManager().getPipelineNullable();
		if(pipeline != null) {
			WorldRenderingPhase phase = pipeline.getPhase();
			Map<RenderStateShard, List<Runnable>> renderStateShardToCommands = phaseRenderStateShardToCommands.get(phase);
			if(renderStateShardToCommands != null) {
				List<Runnable> commands = renderStateShardToCommands.remove(currentRenderStateShard);
				if(commands != null) {
					if(phase != WorldRenderingPhase.NONE) {
						int currentProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
						int normals = GL20.glGetUniformLocation(currentProgram, "normals");
						int specular = GL20.glGetUniformLocation(currentProgram, "specular");
						RenderedGltfModel.NORMAL_MAP_INDEX = ((normals == -1) ? -1 : GL13.GL_TEXTURE0 + GL20.glGetUniformi(currentProgram, normals));
						RenderedGltfModel.SPECULAR_MAP_INDEX = ((specular == -1) ? -1 : GL13.GL_TEXTURE0 + GL20.glGetUniformi(currentProgram, specular));
					}	
					commands.forEach(Runnable::run);
				}
			}
		}
	}
}

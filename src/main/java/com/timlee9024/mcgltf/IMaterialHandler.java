package com.timlee9024.mcgltf;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

public interface IMaterialHandler {

	/**
	 * ShaderMod Texture index, this may change in different Minecraft version.</br>
	 * <a href="https://github.com/sp614x/optifine/blob/master/OptiFineDoc/doc/shaders.txt">optifine/shaders.txt</a>
	 */
	int COLOR_MAP_INDEX = GL13.GL_TEXTURE0;
	int NORMAL_MAP_INDEX = GL13.GL_TEXTURE1;
	int SPECULAR_MAP_INDEX = GL13.GL_TEXTURE3;
	
	IMaterialHandler DEFAULT_INSTANCE = new IMaterialHandler() {
		
		@Override
		public Runnable getVanillaPreMeshDrawCommand() {
			return () -> {
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, MCglTF.getInstance().getDefaultColorMap());
				GL20.glVertexAttrib4f(RenderedGltfModel.vaColor, 1.0F, 1.0F, 1.0F, 1.0F);
				GL11.glEnable(GL11.GL_CULL_FACE);
			};
		}

		@Override
		public Runnable getShaderModPreMeshDrawCommand() {
			return () -> {
				GL13.glActiveTexture(COLOR_MAP_INDEX);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, MCglTF.getInstance().getDefaultColorMap());
				GL13.glActiveTexture(NORMAL_MAP_INDEX);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, MCglTF.getInstance().getDefaultNormalMap());
				GL13.glActiveTexture(SPECULAR_MAP_INDEX);
				GL11.glBindTexture(GL11.GL_TEXTURE_2D, MCglTF.getInstance().getDefaultSpecularMap());
				GL20.glVertexAttrib4f(RenderedGltfModel.vaColor, 1.0F, 1.0F, 1.0F, 1.0F);
				GL11.glEnable(GL11.GL_CULL_FACE);
			};
		}
		
	};
	
	default boolean hasNormalMap() {
		return false;
	}
	
	default Runnable getVanillaPreMeshDrawCommand() {
		return null;
	}
	
	default Runnable getVanillaPostMeshDrawCommand() {
		return null;
	}
	
	default Runnable getShaderModPreMeshDrawCommand() {
		return null;
	}
	
	default Runnable getShaderModPostMeshDrawCommand() {
		return null;
	}

}

package com.timlee9024.mcgltf;

public class DefaultMaterialHandler implements IMaterialHandler {
	
	public class TextureInfo {
		public int index;
	}
	
	public TextureInfo baseColorTexture;
	public TextureInfo normalTexture;
	public TextureInfo specularTexture;
	public float[] baseColorFactor = {1.0F, 1.0F, 1.0F, 1.0F};
	public boolean doubleSided;
	
	public Runnable vanillaPreMeshDrawCommand;
	public Runnable shaderModPreMeshDrawCommand;

	@Override
	public boolean hasNormalMap() {
		return normalTexture != null;
	}

	@Override
	public Runnable getVanillaPreMeshDrawCommand() {
		return vanillaPreMeshDrawCommand;
	}

	@Override
	public Runnable getShaderModPreMeshDrawCommand() {
		return shaderModPreMeshDrawCommand;
	}

}

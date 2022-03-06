package com.timlee9024.mcgltf;

public class DefaultMaterialHandler implements IMaterialHandler {
	
	public class TextureInfo {
		public int index;
		
		public int texCoord;
	}
	
	public TextureInfo colorTexture;
	public TextureInfo normalTexture;
	public TextureInfo specularTexture;
	public float[] color = {1.0F, 1.0F, 1.0F, 1.0F};
	public boolean isDoubleSided;
	
	public Runnable preMeshDrawCommand;

	@Override
	public int texCoordsToActiveIndex(String attribute) {
		if(attribute.startsWith("TEXCOORD_")) {
			int texCoord = Integer.parseInt(attribute.substring(9));
			if(colorTexture != null && colorTexture.texCoord == texCoord) return COLOR_MAP_INDEX;
			if(normalTexture != null && normalTexture.texCoord == texCoord) return NORMAL_MAP_INDEX;
			if(specularTexture != null && specularTexture.texCoord == texCoord) return SPECULAR_MAP_INDEX;
		}
		return -1;
	}

	@Override
	public String getNormalTexCoordsAttribute() {
		return normalTexture == null ? null : "TEXCOORD_" + normalTexture.texCoord;
	}

	@Override
	public Runnable getPreMeshDrawCommand() {
		return preMeshDrawCommand;
	}

}
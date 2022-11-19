package com.modularmods.mcgltf.iris.mixin;

import java.nio.ByteBuffer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.modularmods.mcgltf.iris.IrisRenderingHook;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.VertexFormat;

@Mixin(BufferUploader.class)
public class MixinBufferUploader {

	@Inject(method = "_end(Ljava/nio/ByteBuffer;Lcom/mojang/blaze3d/vertex/VertexFormat$Mode;Lcom/mojang/blaze3d/vertex/VertexFormat;ILcom/mojang/blaze3d/vertex/VertexFormat$IndexType;IZ)V", at = @At("HEAD"))
	private static void before_end(ByteBuffer byteBuffer, VertexFormat.Mode mode, VertexFormat vertexFormat, int i, VertexFormat.IndexType indexType, int j, boolean bl, CallbackInfo ci) {
		if(i <= 0) IrisRenderingHook.irisHookBypassBufferUploaderVextexCountCheck(mode);
	}
}

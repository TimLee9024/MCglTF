package com.modularmods.mcgltf.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.modularmods.mcgltf.iris.IrisRenderingHook;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;

@Mixin(BufferUploader.class)
public class MixinBufferUploader {

	@Inject(method = "_drawWithShader(Lcom/mojang/blaze3d/vertex/BufferBuilder$RenderedBuffer;)V", at = @At("HEAD"))
	private static void before_drawWithShader(BufferBuilder.RenderedBuffer renderedBuffer, CallbackInfo ci) {
		if(renderedBuffer.isEmpty()) IrisRenderingHook.irisHookBypassBufferUploaderVextexCountCheck(renderedBuffer);
	}
}

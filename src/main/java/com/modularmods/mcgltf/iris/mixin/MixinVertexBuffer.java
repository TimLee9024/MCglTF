package com.modularmods.mcgltf.iris.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.modularmods.mcgltf.iris.IrisRenderingHook;
import com.mojang.blaze3d.vertex.VertexBuffer;

@Mixin(VertexBuffer.class)
public class MixinVertexBuffer {

	@Inject(method = "draw()V", at = @At("TAIL"))
	private void afterDraw(CallbackInfo ci) {
		IrisRenderingHook.irisHookAfterVertexBufferDraw();
	}
}

package com.timlee9024.mcgltf.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import com.mojang.math.Matrix4f;

@Mixin(Matrix4f.class)
public interface Matrix4fAccessor {

	@Accessor
	void setM00(float value);
	@Accessor
	void setM01(float value);
	@Accessor
	void setM02(float value);
	@Accessor
	void setM03(float value);
	@Accessor
	void setM10(float value);
	@Accessor
	void setM11(float value);
	@Accessor
	void setM12(float value);
	@Accessor
	void setM13(float value);
	@Accessor
	void setM20(float value);
	@Accessor
	void setM21(float value);
	@Accessor
	void setM22(float value);
	@Accessor
	void setM23(float value);
	@Accessor
	void setM30(float value);
	@Accessor
	void setM31(float value);
	@Accessor
	void setM32(float value);
	@Accessor
	void setM33(float value);

}

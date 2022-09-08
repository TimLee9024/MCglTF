# MCglTF
A 3D model loader library which load glTF format file and prepare the required techniques to render the model for Minecraft Modding enviroment.
Various features from glTF spec are available but still remain a good compatibility and performance.

[![](https://cf.way2muchnoise.eu/title/mcgltf.svg)](https://www.curseforge.com/minecraft/mc-mods/mcgltf) [![](https://cf.way2muchnoise.eu/versions/mcgltf.svg)](https://www.curseforge.com/minecraft/mc-mods/mcgltf) [![](https://cf.way2muchnoise.eu/mcgltf.svg)](https://www.curseforge.com/minecraft/mc-mods/mcgltf)
## Usages
The example codes for rendering Block, Item, and Entity
- https://github.com/TimLee9024/MCglTF-Example
## Features
- [x] GLTF format (Embedded resources or via ResourceLocation)
- [x] GLB format
- [x] UVs
- [x] Normals
- [x] Tangents
- [x] Vertex colors
- [x] Materials ([Require OptiFine and supported ShaderPack for PBR](https://github.com/TimLee9024/MCglTF/wiki/How-to-make-PBR-Materials-working-with-OptiFine))
- [x] Textures
- [ ] Mutiple texture coordinates (For compatibility reason with Vanilla)
- [x] Rig
- [x] Animations (multiple)
- [x] Morph targets
- [x] Zero-scale node culling (https://github.com/KhronosGroup/glTF/pull/2059)
## Reason for Not comptible with Iris Shaders
Despite Iris Shaders is built upon in OptiFine specification about shader pack for Minecraft, there are a lots of internal implementation differences between two.

For example, you cannot get current GL program that is proper for Item/Entity/BlockEntity renderer like what OptiFine did. Instead, they switch their GL program during [GlStateManager#_drawArrays()](https://github.com/IrisShaders/Iris/blob/trunk/src/main/java/net/coderbot/iris/mixin/state_tracking/MixinGlStateManager.java), which making it hard to deal with glTF model rendering code.

Aside from that, Iris Shaders does not fully support every features from shaderpack. And you can still using OptiFine in Fabric with OptiFabric installed.

So it is most likely that MCglTF will still not support Iris Shaders in the future.
## System requirements
- OpenGL 4.3 and higher
## Credit
- JglTF by javagl : https://github.com/javagl/JglTF
- Mikk Tangent Generator by jMonkeyEngine : https://github.com/jMonkeyEngine/jmonkeyengine
# MCglTF
A 3D model loader library which load glTF format file and prepare the required techniques to render the model for Minecraft Modding enviroment.
Various features from glTF spec are available but still remain a good compatibility and performance.
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
- [x] Materials ([Require ShaderMod and supported ShaderPack for PBR](https://github.com/TimLee9024/MCglTF/wiki/How-to-make-PBR-Materials-working-with-Optifine))
- [x] Textures
- [ ] Mutiple texture coordinates (For compatibility reason with Vanilla)
- [x] Rig
- [x] Animations (multiple)
- [x] Morph targets
- [x] Zero-scale node culling (https://github.com/KhronosGroup/glTF/pull/2059)
- [x] Custom material handler for excuting specical render commands for specified meshes
## System requirements
- OpenGL 4.3 and higher
## Credit
- JglTF by javagl : https://github.com/javagl/JglTF
- Mikk Tangent Space Generator by jMonkeyEngine : https://github.com/jMonkeyEngine/jmonkeyengine

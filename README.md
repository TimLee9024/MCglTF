# MCglTF
A 3D model loader library which load glTF format file and prepare the required techniques to render the model for Minecraft Modding enviroment.
Various features from glTF spec is available but still remain a good compatibility and performance.

## Features
- [x] GLTF format (Embedded resources or via ResourceLocation)
- [x] GLB format
- [x] UVs
- [x] Normals
- [x] Tangents
- [x] Vertex colors
- [x] Materials (Require ShaderMod and supported ShaderPack for PBR)
- [x] Textures
- [x] Rig
- [x] Animations (multiple)
- [x] Morph targets
- [x] Zero-scale node culling
- [x] Custom material handler for excuting specical render commands for specified meshes

## System requirements
- OpenGL 4.3 and higher

## Known issues
- 1.16.5 with Optifine: If you put any Item in the Item Frame, the normal maps of that Item model (even Vanilla model without MCglTF installed) will not taking any effect. This is a bug that haven't fixed by Optifine.
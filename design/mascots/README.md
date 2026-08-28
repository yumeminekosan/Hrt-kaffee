# Hrt-kaffee module cats

Sixteen module cats are kept in two synchronized forms:

- `psd/<module>.psd`: layered 512×512 source for further rigging.
- `../../public/mascots/cats/<module>/`: transparent web layers used by the page.

The pixel direction is 64-bit-era, hard-edged sprite art with
menhera-inspired black/pink/lavender accessories. Every source is reduced to a
128×128 pixel master with a 64-colour palette before nearest-neighbour export.
The PSD layer order is
`body`, `tail`, `head`. The three layers partition the
original sprite, so their neutral transforms reconstruct the source exactly.
The browser controller applies small independent transforms to those layers for
idle breathing, head tilts, tail movement, button pounces, calculation, success,
error, reset/sleep and petting reactions.

These PSDs are rig-ready art sources rather than Cubism binaries. A native
Live2D Cubism export would additionally require manual deformers and a generated
`.moc3` model; the page intentionally keeps the zero-runtime layered animation
so GitHub Pages can render it without a proprietary SDK.

# Hrt-kaffee AR · Kotlin rigorous core

This directory supplies the hidden mathematical core and the Kotlin browser engine embedded in the existing Hrt-kaffee simulator. It contains:

- `rigor-core`: dependency-light Kotlin/JVM mathematics and domain contracts.
- `composeApp`: an original tactical-terminal UI built with Compose Multiplatform.
- `webApp`: the responsive Kotlin/Wasm finasteride input/result/curve interface deployed to GitHub Pages.
- `embeddedEngine`: the Kotlin/JS controller integrated directly into the original page; it renders the finasteride/dutasteride, bicalutamide PK–AR, transdermal-estradiol and progestogen–PR/GnRH inputs, metrics and curves without replacing the rest of Hrt-kaffee.
- `rigor-core/.../NuclearQuantumBinding.kt`: a hard boundary from thermal de Broglie scale to externally calibrated free-energy/rate corrections; no wavelength-to-binding shortcut.
- `docs/FLOW_AUDIT.md`: arrow-by-arrow implementation audit and conceptual-question resolution.
- `docs/RIGOR_MATRIX.md`: claim-by-claim status, assumptions, and falsification checks.
- `docs/BICALUTAMIDE_MODEL.md`: equations, units, calibration residuals, counterfactuals and validation gates for the direct AR-competition module.
- `docs/BICALUTAMIDE_E2_BRIDGE.md`: the hard E2-only identifiability gate and lab-anchored WT AR threshold inversion.
- `docs/BICALUTAMIDE_QUESTION_COVERAGE.md`: row-level disposition of all 50 AR-library and 103 general ligand–receptor questions.

## Run

Install JDK 17 and Gradle 9.5.0 (the exact Gradle version pinned in CI), then run:

```bash
gradle test
gradle :composeApp:run
gradle :webApp:wasmJsBrowserDevelopmentRun
gradle :embeddedEngine:jsBrowserDevelopmentRun
```

The production browser bundle is generated with:

```bash
gradle :webApp:jvmTest :webApp:wasmJsBrowserDistribution \
  :embeddedEngine:jvmTest :embeddedEngine:jsBrowserDistribution
```

`webApp` keeps exact normalized rational arithmetic for the declared 0..10,000
basis-point control domain. Its JVM parity test evaluates an intervention grid
against the arbitrary-precision `rigor-core` implementation before the Wasm
bundle is accepted for deployment.

## Non-negotiable model boundary

Direct androgen-receptor competition and upstream 5α-reductase suppression are separate interventions. The UI never reports their sum as a universal “AR blockade percentage”. It reports four counterfactual signals, Shapley-attributed contributions inside the declared equilibrium model, and a non-additivity term.

Every output carries one of these evidence classes:

1. exact identity;
2. theorem conditional on named assumptions;
3. numerical certificate with a residual;
4. Monte Carlo estimate with an interval;
5. illustrative parameterization.

No `Double` trajectory is presented as a proof. This is research software, not medical advice.

The complete CTMC → Kurtz/LDP/Doob/PDE/topology map remains in Kotlin and the audit documents. It is intentionally absent from the user interface, which exposes only regimen inputs, flux/concentration/target-engagement outputs and time curves. The bicalutamide module keeps active-R PK, unbound tissue transfer, competitive occupancy, efficacy and endocrine feedback as separate layers; its headline is a same-androgen counterfactual signal, not a clinical blockade claim. Its estradiol bridge displays transdermal exposure but keeps dose inference locked until baseline and contemporaneous residual tissue-equivalent T/DHT are supplied; even then it reports only a WT model threshold, not individual HRT sufficiency. The transdermal module reduces interface-partitioned patch/skin diffusion to three finite-volume skin states, then couples central/peripheral population PK to SHBG/albumin mass balance and separate ERα/ERβ/GPER engagement signals. The FDA Vivelle-Dot label supplies population delivery/area/PK anchors; it is not an individual dose recommendation. Finasteride and dutasteride remain upstream 5α-reductase inhibitors, never direct AR competitors. Progestogens bind PR and drive a delayed feedback state; no direct GnRH-receptor antagonism is asserted.

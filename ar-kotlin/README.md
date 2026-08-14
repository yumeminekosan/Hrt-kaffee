# Hrt-kaffee AR · Kotlin rigorous core

This directory is deliberately isolated from the existing Next.js simulator. It contains:

- `rigor-core`: dependency-light Kotlin/JVM mathematics and domain contracts.
- `composeApp`: an original tactical-terminal UI built with Compose Multiplatform.
- `docs/FLOW_AUDIT.md`: arrow-by-arrow implementation audit and conceptual-question resolution.
- `docs/RIGOR_MATRIX.md`: claim-by-claim status, assumptions, and falsification checks.

## Run

Install JDK 17 and Gradle 9.5.0 (the exact Gradle version pinned in CI), then run:

```bash
gradle test
gradle :composeApp:run
```

## Non-negotiable model boundary

Direct androgen-receptor competition and upstream 5α-reductase suppression are separate interventions. The UI never reports their sum as a universal “AR blockade percentage”. It reports four counterfactual signals, Shapley-attributed contributions inside the declared equilibrium model, and a non-additivity term.

Every output carries one of these evidence classes:

1. exact identity;
2. theorem conditional on named assumptions;
3. numerical certificate with a residual;
4. Monte Carlo estimate with an interval;
5. illustrative parameterization.

No `Double` trajectory is presented as a proof. This is research software, not medical advice.

# Route-aware PK and population Monte Carlo audit

This note defines the only general-purpose PK path used by the deployed simulator. It is a research sensitivity model, not a fitted individual model and not a dosing recommendation.

## Scope and structural choice

The live route selector uses a one-compartment disposition model with an explicit administration site. Oral, sublingual, transdermal, intramuscular-depot and subcutaneous-depot labels retain distinct route identity and route-specific parameters. They currently share a **lumped first-order input** topology; that is a declared reduction, not evidence that dissolution, skin transport and oil-depot hydrolysis have the same biology. IV bolus is represented as a central-compartment jump.

For an extravascular dose at time `t_d`, with amounts in micrograms and time in hours,

\[
A_s(t_d^+)=A_s(t_d^-)+1000D m,\qquad
\dot A_s=-k_aA_s,
\]

\[
\dot A_c=F k_aA_s-k_eA_c,\qquad
k_e=CL/V,\qquad C=A_c/V.
\]

`D` is the entered dose in mg, `m` is the declared active-moiety mass fraction, `F` is the fraction of released active-moiety equivalent that reaches the central compartment, `CL` is L/h, `V` is L and `C` is numerically ng/mL. Estradiol and testosterone ester entries declare `m`; it is never silently inferred. For IV bolus, `FDm` enters `A_c` directly.

The exact transition over `Δt` is

\[
A_s'=A_s e^{-k_a\Delta t},
\]

\[
A_c'=A_c e^{-k_e\Delta t}
 +F k_a A_s\frac{e^{-k_a\Delta t}-e^{-k_e\Delta t}}{k_e-k_a},
\]

with the continuous limit `F k_a A_s Δt e^{-k_eΔt}` when `k_a≈k_e`. Every scheduled dose time and analysis-window boundary is inserted into the grid. Concentrations use the right limit at a dose for plotting; the left limit at the closing boundary is used for `Cmax`, `Cmin` and `AUCτ`, so a new IV bolus cannot leak into the preceding interval.

The bookkeeping identity is

\[
A_{admin}=A_s+A_c+A_{unavailable}+A_{eliminated}.
\]

The relative residual of this identity is returned for every simulation.

## What oral and injection curves do—and do not—mean

| Route | Implemented physical interpretation | Not identified by this model |
|---|---|---|
| Oral / sublingual | First-order appearance plus systemic first-order elimination; slow `k_a` may express an apparent absorption-limited terminal phase | Gut segments, enterohepatic cycling, estrone conjugate pools, food effects, true first-pass organ kinetics |
| IM / SC depot | A finite depot releases ester/active-moiety equivalent by one first-order rate before systemic disposition | Injection-site geometry, oil partition, dissolution, ester hydrolysis as separate steps, local blood flow, formulation or site covariates |
| Transdermal | A finite application-site amount with lumped first-order delivery | Multilayer diffusion, patch zero-order input, finite-dose skin depletion and site-dependent permeability; those remain in the separate Kotlin transdermal module |
| IV bolus | Instantaneous central input followed by elimination | Distribution phase; a two-compartment model is required if early biexponential data identify it |

The oral estradiol-valerate literature includes large between-person spread and measured metabolites, so a single parent one-compartment curve is deliberately not labelled a complete biotransformation model ([Zimmermann et al., PMID 9793623](https://pubmed.ncbi.nlm.nih.gov/9793623/)). Injection-era studies are used only as route/shape anchors, not as an individualized HRT population fit ([Goebelsmann et al., PMID 7169965](https://pubmed.ncbi.nlm.nih.gov/7169965/); [Schiff et al., PMID 2987096](https://pubmed.ncbi.nlm.nih.gov/2987096/)).

## Population Monte Carlo

Monte Carlo represents uncertainty or between-subject sensitivity in **parameters**, not white noise injected into PK states. For positive parameters `θ∈{CL,V,k_a}`,

\[
\theta_i=\theta_{typ}\exp(\omega_\theta z_i),\qquad
\omega_\theta=\sqrt{\log(1+CV_\theta^2)}.
\]

Thus the displayed typical value is the distribution median. `CL` and `V` may share a declared Gaussian correlation; other effects are independent. `F` uses a logistic-normal draw and therefore remains strictly between zero and one. The UI CV is a user-supplied sensitivity assumption, **not fitted IIV**. IOV is not implemented.

Measurement error is a separate optional layer and never changes latent PK metrics:

\[
y_j=\max\{0,C_j\exp(\sigma_p\epsilon_j-\tfrac12\sigma_p^2)+\sigma_a\epsilon'_j\}.
\]

The simulator returns latent `C`, optional `observedC`, sampled parameters, seed, route, `AUCτ`, its exact analysis window and mass-balance residual. A fixed 32-bit seed makes parameter and observation draws reproducible. Percentiles use linear interpolation. Summaries are median, 5/25/75/95 percentiles, mean and Monte Carlo standard error; they are not posterior credible intervals.

This separation follows the fit-for-purpose principle in the [FDA Population Pharmacokinetics guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/population-pharmacokinetics): structural choice, variability, uncertainty and validation must answer the stated simulation question. Because this repository has no fitted multi-individual NLME dataset for these entries, its population mode is a sensitivity simulator rather than a validated PopPK model.

## Rejected or gated methods

| Former choice | Disposition | Reason / reopening gate |
|---|---|---|
| Störmer–Verlet, Yoshida, Forest–Ruth | Removed from the deployed PK path | Compartment PK is dissipative and has no declared Hamiltonian phase space. Reopen only for a derived Hamiltonian subsystem with a preservation invariant and benchmark. |
| Itô / Stratonovich state SDE, Heun, Milstein | Removed | No biological diffusion coefficient or stochastic-calculus convention was identified. Reopen with a generative state-noise mechanism and data that separate it from IIV/IOV/residual error. |
| Jump diffusion | Removed | Scheduled doses remain explicit deterministic jump events; no unscheduled jump intensity or mark law is identified. |
| GARCH state volatility | Removed | GARCH is a conditional-variance observation/time-series model, not a default PK mass-balance mechanism. Reopen only as a separately validated observation process. |
| Generic 2-CMT toggle | Removed | A second compartment without route-specific data and identifiable intercompartmental parameters creates unsupported degrees of freedom. |
| Browser GPU Monte Carlo | Removed from the audited path | The former shader used an unverified pseudo-normal generator and returned incomplete exposure metrics. Reopen only after seeded distributional tests and CPU parity for full trajectories and all summaries. |

These removals do not claim that stochastic, jump, two-compartment or mechanistic depot models are universally inappropriate. They say only that this repository has not yet supplied the biological object, identifiable parameters and validation evidence needed to expose them as interchangeable choices.

## Machine gates

The JavaScript test suite checks:

1. positivity and relative mass-balance residual;
2. exact concentration invariance at shared times across output step sizes;
3. dose-event alignment and the IV left-limit analysis convention;
4. long-run `AUCτ≈1000DmF/CL` for a linear steady-state interval;
5. same-seed equality and different-seed separation;
6. separation of latent exposure from simulated measurement error;
7. canonical database use and absence of unsupported solver selectors in the deployed page;
8. exactly 50 AR plus 107 general question dispositions with unique IDs.

Passing these gates certifies implementation identities and reproducibility only. It does not validate any curve for a person.

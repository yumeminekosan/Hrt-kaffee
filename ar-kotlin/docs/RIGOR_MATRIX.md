# Rigor matrix

This matrix is the definition of “done”. A green numerical curve never upgrades a conditional theorem to an exact result.

| Layer | Implemented object | Evidence class | Machine check | Remaining analytical gate |
|---|---|---|---|---|
| AR mechanism split | Four exact counterfactual signals; Shapley contributions; non-additivity | Exact identity inside declared reduced model | Exact rational equalities and switch-off tests | Fit/identify biological parameters before any empirical claim |
| Microscopic dynamics | Finite reaction network and exact row generator `Q` | Exact identity | Reachable-state enumeration; signs; exact row sums | State-space choice remains a modelling decision |
| Free energy / activity | Formal Gibbs weights; exact `γc/c°`; local detailed-balance edge audit | Exact identity for declared inputs | Exact rate, weight, and reciprocal reservoir ratios | Identify physical AR free energies, activity coefficients, and reservoirs |
| Nuclear-quantum boundary | Thermal `λ_th`; external `ΔΔG_bind`/`ΔΔG‡` projection; exact named rate calibration | Numerical scale + illustrative/model input | `m⁻¹/²T⁻¹/²` scaling; forward/reverse ratio consistency; provenance; unknown reaction rejection | Supply system-specific PIMD/quantum-rate or experimental correction and justify Markovian coarse-graining |
| Random time change | Reaction-labelled Gillespie path; clock/state identity; compensated counts | Exact integer identity + stochastic sample | `X(t)−X(0)=ΣνN_r(t)` exactly; nonnegative brackets | Infinite-state extensions need Lyapunov non-explosion/localization |
| Martingale | Dynkin terminal value and exact carré-du-champ bound | Exact identity + Monte Carlo estimate | Exact `Qf`; seeded 95% estimate test | Infinite-horizon convergence needs its own uniform-integrability hypothesis |
| Thermodynamics | Exact stationary law; detailed-balance audit; formal cycle affinity; numerical entropy production | Exact identity + numerical certificate | `πQ=0`, `Σπ=1`, pair-flow equality, log roundoff diagnostic | Local detailed-balance interpretation needs declared reservoirs/energies |
| Kurtz limit | Density symbol and drift from the same reaction table | Theorem under assumptions | Gate requires density scaling, Lipschitz, compact containment, initial convergence | Supply problem-specific proofs/witnesses; RK4 is not the proof |
| Fluid trajectory | RK4 with step doubling | Numerical certificate | Explicit discretization indicator | Replace with interval integration if a rigorous enclosure is required |
| Exponential nonlinear generator | `N⁻¹e^(−Nf)L_Ne^(Nf)` on linear exponential tests | Exact scaled intensities + numerical exponential evaluation | Finite-N/limit Hamiltonian residual | Uniform convergence domain remains model-specific; no Fourier transform is used |
| Hamiltonian | `H(x,p)=Σβ(exp(p·ν)-1)` from the same jump channels | Model-derived formula | `H(x,0)=0`; gradient test | Domain/coercivity must be checked per model |
| Legendre–Fenchel | Damped Newton dual solver | Numerical certificate | Gradient residual and convergence flag | Boundary/unreachable velocities require separate convex analysis |
| Hamilton–Jacobi | Residual evaluator `∂tV+H(x,∇V)` | Numerical diagnostic only | Explicit residual value | No viscosity-solution theorem is claimed by the evaluator |
| Sample-path LDP / quasipotential | Conditional LDP and quasipotential theorem objects | Theorem under named assumptions | Missing exponential-tightness/good-rate/coercivity witnesses block construction | Prove these properties for the selected AR scaling |
| Minimum-action path | Fixed-time/fixed-mesh steepest-descent candidate | Numerical certificate | Dual and stationarity residuals; nonnegative action check | No global minimum or time infimum is claimed |
| Exponential tilt | Non-stochastic `TiltedOperator` | Structural/type invariant | Cannot satisfy `StochasticGenerator` API | Observable and tilt domain must be specified |
| Doob process | Principal eigenpair then driven generator | Numerical certificate | Positive vector, eigen-residual, diagonal consistency, exact constructed row sum to tolerance | Spectral convergence tolerance is explicit, never hidden |
| Metastability | Quasipotential exit gate; exact mean hitting times; killed-generator quasi-stationary mode | Conditional theorem + exact identity + numerical certificate | Scale-separation gate; exact linear system; eigen-residual | Establish AR-specific internal-mixing/exit asymptotics |
| Spatial IPS | Periodic lattice lift of the same local network with `D L²` hops | Exact generator construction | Reverse hop pairs; exact generator rows | Add interaction/Gibbs factors only when spatial physics requires them |
| Spatial PDE | Same-network reaction drift plus conservative 1D finite-volume step | Conditional theorem + numerical certificate | Spatial/L²/K/replacement/tightness gates; CFL, positivity, conservation residual | Prove AR-specific replacement/local-equilibrium and tightness estimates |
| Chain complex | Simplicial faces and boundary; stoichiometric two-term complex | Exact identity | `∂²=0`; exact left/right nullspaces | Interpret selected cycles/conservation laws physically |
| Finasteride PK/PD | Saturable SRD5A2 binding, SRD5A1 inhibition and serum-DHT turnover | Identified population parameterization + numerical certificate | JVM RK4 step-halving; JVM/Wasm trajectory parity; 0–1 bounds | Re-identify for an individual; doses above 5 mg/day are repeated-dose extrapolations |
| Dutasteride PK/PD | Dual SRD5A1/SRD5A2 competitive engagement, time-dependent enzyme inactivation and serum-DHT turnover | Regulatory/literature-anchored population parameterization + numerical certificate | 0.5 mg/day 1/2-week and 24-week calibration windows; RK4 step-halving; 0–1 bounds | Accessibility/inactivation parameters are population-effective, not direct structural constants or an individual fit |
| Bicalutamide PK–AR | Active-R Bateman PK, unbound plasma/tissue transfer, T/DHT/R-BIC competition, efficacy-separated signal and optional HPG stress state | Regulatory anchor + assay-transfer parameterization + exact occupancy identities + numerical residual | Exact 50 mg Cmax/Tmax back-calculation; Css external z-residual; receptor partition; zero-dose and dose-monotonic gates; JVM/JS parity; W741L boundary test | Human target-tissue Kp,uu, WT functional transfer, HRT population feedback and external concentration–effect validation remain unidentified |
| E2–bicalutamide threshold bridge | Transdermal E2 exposure context → observed baseline/current tissue-equivalent T/DHT → WT AR forward threshold inversion | Structural link + explicit research inputs; not an individual estimate | E2-only hard gate; 0 mg boundary; dose monotonicity; conservative threshold ≥ central; JVM/JS parity; WT-only UI gate | E2-only HPG function, serum→tissue mapping, individual bicalutamide PK, clinical HRT endpoints and safety joint model remain unidentified |
| Progestogen PR/GnRH feedback | Oral PK, ligand–PR occupancy, delayed E2-primed GnRH pulse feedback and post-last-dose threshold crossing; direct GnRH-receptor antagonism explicitly excluded | Structure/literature-anchored population projection + numerical certificate | P4 five-dose 100 mg label Cmax window; RK4 step-halving; 0–1 bounds; JVM/JS coverage parity; exact full-flow network test | Synthetic progestogens are relative structure/PK extrapolations; pulse feedback and its threshold window are not individualized and depend on endocrine context |
| Integrated UI | Finasteride/dutasteride, bicalutamide PK–AR and progestogen dose/time inputs, endpoint metrics and time curves inside the original Hrt-kaffee page | Presentation only | Kotlin/JVM↔Kotlin/JS trajectory parity; browser build; mascot-layer reconstruction and unchanged-page identity checks | UI is not an evidence source; the mathematical flow is intentionally hidden |

## Assumption policy

`AssumptionStatus` has only three values:

- `CHECKED`: a named witness exists in the current scope;
- `DECLARED`: a modelling choice is visible but not proved;
- `FAILED`: certification is blocked.

`Evidence` rejects a failed assumption. Numerical certificates must contain at least one residual/tolerance pair. Exact identities cannot contain floating-point diagnostics.

## Parameter policy

The default AR values are dimensionless and tagged `ILLUSTRATIVE_PARAMETERIZATION`. The bicalutamide
path calibrates only a lumped active-R concentration scale to regulatory single-dose PK, then carries
assay-specific affinity and unmeasured tissue transfer as explicit sensitivity parameters. The finasteride
path uses the published Suzuki et al. population PK/PD parameters; the dutasteride path uses regulatory
PK anchors and population DHT time-course calibration. The P4 path uses label PK, the observed PR–P4
structure and delayed human neuroendocrine feedback constraints; synthetic progestogens remain relative
extrapolations. All remain population parameters rather than an individual fit. No result may be described as a patient-specific estimate,
clinical efficacy guarantee, dose conversion, or safety recommendation.

The default quantum panel evaluates only `λ_th=h/sqrt(2πmk_BT)`. It deliberately leaves the AR CTMC rates unchanged. A rate update requires a separate `ExactQuantumRateCalibration` whose source and named reaction multipliers are explicit.

# AR model equations and conventions

## 0. Finasteride is an enzyme inhibitor, not an AR competitor

The finasteride path is kept separate from the direct AR-competition coordinate. Structurally,
human SRD5A2 is a seven-transmembrane enzyme whose inhibited state contains an
NADP-dihydrofinasteride adduct in the membrane cavity (PDB `7BW1`; Zhang et al., *Nature
Communications* 11, 5430 (2020), DOI `10.1038/s41467-020-19249-z`). The microscopic model
therefore uses `SRD5A2_FIN`, never `FIN_AR`.

The user-facing time course is the population PK/PD model identified by Suzuki et al., *Drug
Metabolism and Pharmacokinetics* 25 (2010) 208–213, DOI `10.2133/dmpk.25.208`. With gut
amount `Xg` (nmol), free plasma concentration `Cc` (nM), bound type-2 enzyme amount `XcE`
(nmol), and total enzyme amount `Etot`, the hidden equations are

```text
dXg/dt  = -ka Xg
dCc/dt  = ka F Xg/Vc + koff XcE/Vc - ke Cc - kon Cc (Etot-XcE)
dXcE/dt = kon Cc Vc (Etot-XcE) - koff XcE
I2       = XcE/Etot
I1       = Cc/(Cc+Ki1)
```

For normalized serum DHT `D`, type-2 contribution `f2`, and `Kin=kout` at baseline,

```text
dD/dt = kout [f2(1-I2) + (1-f2)(1-I1) - D].
```

The identified values used by both JVM and Wasm are `F=0.8`, `ka=1.87 h⁻¹`, `ke=0.177
h⁻¹`, `Vc=73.7 L`, `kon=0.0293 nmol⁻¹ h⁻¹`, `koff=0.0185 h⁻¹`, `Etot=320 nmol`,
`f2=0.574`, `kout=0.188 h⁻¹`, and `Ki1=220 nM`. The fitted coarse-grained dissociation scale is

```text
Kd,eff = koff/(kon Vc) = 0.00857 nM.
```

This fitted `Kd,eff` is not promoted to a universal microscopic free energy: the structural
mechanism contains intermediate chemistry and a long-lived adduct. Bull et al., *JACS* 118
(1996) 2359–2365, DOI `10.1021/ja953069t`, measured mechanism-based processing for human
type 2 with `ki/Ki = 1×10⁶ M⁻¹ s⁻¹` and an enzyme-inhibitor-complex release half-life near one
month. The population model instead retains the reversible effective compartment because that
model was identified against plasma-finasteride and serum-DHT time courses.

Once-daily doses are pulses into `Xg`; RK4 integrates only the displayed population projection.
The JVM implementation certifies it by step halving. Doses above 5 mg/day are displayed as
repeated-dose extrapolations, not inferred extra clinical benefit. The finite molecule-count
network in `FinasterideMicroscopicNetwork` supplies the strict CTMC/generator/limit/large-
deviation/Doob/spatial/chain-complex layers, while the browser exposes only inputs, endpoint
metrics, and curves. As in the AR network, any nuclear-quantum correction must enter through an
`ExactQuantumRateCalibration` carrying externally identified `ΔΔGbind`/`ΔΔG‡`, provenance and
named reaction IDs; the thermal de Broglie wavelength alone never changes a finasteride rate.

## 0.5. Dutasteride is a dual enzyme inhibitor, not an AR competitor

Dutasteride adds competitive, time-dependent inhibited states for both `SRD5A1` and `SRD5A2`.
The experimentally observed `7BW1` structure contains finasteride rather than dutasteride; the shared
4-azasteroid/NADP-adduct geometry is therefore recorded only as a structural template/inference, never
as a direct dutasteride complex. The molecule is `C27H30F6N2O2`, molecular weight `528.53 g mol⁻¹`.

The oral population projection uses the regulator-reported `F=0.60`, `Tmax=1–3 h`, `V=300–500 L`
and terminal half-life `3–5 weeks`. With a 35-day central half-life, `ka=1.5 h⁻¹`, `V=300 L`, gut
amount `Xg` and plasma concentration `C`, the hidden PK equations are

```text
dXg/dt = -ka Xg
dC/dt  = ka F Xg/V - ke C,             ke = ln(2)/(35·24 h).
```

For active enzyme fractions `E1`, `E2`, structural competitive scales `Ki,1=3.9 nM`, `Ki,2=1.8
nM`, and declared population accessibility scale `s=12`,

```text
θi       = C/(C+s Ki,i)
dEi/dt   = krec(1-Ei) - kinact θi Ei
dD/dt    = kout [f2 E2 + (1-f2) E1 - D].
```

The population-effective values are `krec=0.006 h⁻¹`, `kinact=0.12 h⁻¹`, `f2=0.80` and
`kout=0.188 h⁻¹`. The accessibility scale is not represented as a microscopic dissociation constant;
it absorbs protein binding, tissue access and unmodelled enzyme turnover at this reduced level. It is
calibrated so the 0.5 mg/day projection remains inside the reported DHT reductions of approximately
85% after one week, 90% after two weeks and 94–95% on the longer dose-ranging horizon. RK4
step-halving and JVM/browser parity certify only the numerical projection.

`DutasterideMicroscopicNetwork` supplies the finite dual-enzyme CTMC. The same reaction table feeds
the exact generator, random-time-change/jump-martingale audit, density symbol and Hamiltonian,
tilt/Doob construction, optional spatial lift and stoichiometric chain complex. The original page
renders only regimen controls, enzyme-inhibition observables, DHT suppression and curves.

## 0.75. Progesterone binds PR and feeds back on the GnRH pulse generator

Progesterone is not encoded as a competitive antagonist at the GnRH receptor. The structural anchor is
the experimentally observed human progesterone-receptor ligand-binding domain with progesterone,
PDB `1A28` at `1.8 Å` resolution (Williams and Sigler, *Nature* 393 (1998) 392–396, DOI
`10.1038/30775`). Human recombinant PR competitive-binding measurements place progesterone agonists
in the low-nanomolar range; the browser uses an apparent P4 PR half-occupancy value of `9.01 nM` from
the comparative assay used for its cross-progestin profiles.

For gut amount `Xg`, total plasma concentration `C`, PR-bound fraction `B`, and delayed feedback state
`F`, the hidden population projection is

```text
dXg/dt = -ka Xg
dC/dt  = ka Fa Xg/V - ke C
dB/dt  = kon C(1-B) - koff B,           koff = kon KPR
dF/dt  = kfb(B-F)
SGnRH   = smax F,                        AGnRH = 1-SGnRH.
```

For oral micronized P4, `ka=2.50 h⁻¹`, `ke=ln(2)/16.8 h⁻¹`, `V=50 L` and an effective systemic
scale `Fa=0.00578` reproduce the label mean `Cmax=17.3 ng/mL` after five daily 100 mg doses while
retaining the reported terminal phase. `Fa` here is a reduced-model calibration scale, not a claim about
an individual's absorbed fraction. The label reports strong between-person dispersion and does not
establish an individual response. The feedback half-time is declared as `36 h` and the conservative
population ceiling is `smax=0.55`; this prevents instantaneous PR occupancy from being presented as
instantaneous or near-complete GnRH suppression.
Human pulse studies support PR-mediated gonadotropin feedback but show context dependence: E2/P4
exposure over days suppresses LH pulse frequency, while a single P4 exposure in E2-pretreated women
did not reduce pulse frequency within 12 hours. The UI therefore labels the feedback curve as an
E2-primed population projection.

The page also accepts a selected absolute `SGnRH` threshold and evolves the same state after one final
scheduled dose with no further doses. It reports the final downward threshold crossing and, if supplied,
maps that model window to the chosen last-dose date. This is a PR–GnRH signal coverage window only: it
does not date loss of subjective effects, body changes, breast tissue, estradiol exposure, identity, or
any other individual outcome.

`ProgestogenGnRHMicroscopicNetwork` contains reversible absorption, elimination, ligand–PR binding,
and pulse-generator ready/inhibited channels. That one table feeds the exact generator, random-time-
change identity, nonlinear generator/Hamiltonian, tilt/Doob/Gillespie construction, optional spatial
lift and stoichiometric chain complex. Synthetic progestogen outputs are explicitly marked as relative
structure/PK extrapolations rather than P4-calibrated GnRH efficacy claims.

## 1. Mechanisms remain separate

The reduced panel has two control coordinates:

- direct competition `a ∈ [0,1]`, which changes the antagonist binding weight;
- 5α-reductase inhibition `u ∈ [0,1]`, which changes the upstream `T → DHT` production rate.

They are not added into a universal blockade percentage.

With fixed normalized testosterone exposure `T`, the declared quasi-steady relation is

```text
DHT(u) = k5α (1-u) T / kclear.
```

The competitive binding weights are

```text
wT = T / KT,
wD = DHT(u) / KD,
wA = Amax a / KA,
Z  = 1 + wT + wD + wA.
```

The model-internal signal is

```text
S(a,u) = (εT wT + εD wD + εA wA) / Z.
```

Every quantity above is evaluated with `Rational`; `Double` appears only when the UI renders a percentage.

## 2. Four counterfactual worlds

```text
S00 = S(0,0)   control
S10 = S(a,0)   direct competitor only
S01 = S(0,u)   5αR suppression only
S11 = S(a,u)   both mechanisms
```

The UI reports `S11/S00`, not a total blockade claim. The Shapley decomposition is exact inside this four-world model:

```text
φdirect   = [(S00-S10) + (S01-S11)] / (2 S00)
φupstream = [(S00-S01) + (S10-S11)] / (2 S00)
φdirect + φupstream = (S00-S11)/S00.
```

It also reports non-additivity

```text
Δ = combined suppression - direct-only suppression - upstream-only suppression.
```

These identities do not validate the illustrative parameters or justify clinical extrapolation.

## 3. Microscopic jump process

The finite chemical master equation contains the reversible channels:

```text
T + AR   ⇄ T·AR
DHT + AR ⇄ DHT·AR
A + AR   ⇄ A·AR
T        ⇄ DHT
```

Only the forward `T → DHT` rate is multiplied by `(1-u)`. Direct competition enters through the antagonist population and never changes that conversion rate.

For state `x`, reaction molecularity `m_r`, and system scale `N`, stochastic mass action uses falling factorials with density-dependent scaling:

```text
qᶰr(x) = kr N^(1-mr) ∏s (xs)_(ν-r,s).
```

Zero-order reactions therefore carry an `N` factor, unary rates are unchanged, and binary association rates carry `1/N`. `DensityScaledReactionFamily` constructs this exact rational family and scales the initial counts by `N`.

The row-convention generator is constructed exactly:

```text
Qxy = Σr:x+νr=y qr(x),  x≠y
Qxx = -Σy≠x Qxy.
```

## 4. One source, all downstream objects

`ArRigorousPipeline` creates one `ReactionNetwork`. That same object yields:

- the exact finite generator `Q`;
- reaction-labelled random-time-change clocks, Dynkin drift `Qf`, and carré-du-champ;
- exact stationary flows and formal cycle affinities;
- the density symbol `βr` and Kurtz drift `b(x)=Σrνrβr(x)`;
- the finite exponential nonlinear generator `N⁻¹e^(−Nf)L_Ne^(Nf)`;
- the jump Hamiltonian `H(x,p)=Σrβr(x)(exp(p·νr)-1)`;
- the Legendre–Fenchel dual `L(x,v)=sup_p[p·v-H(x,p)]`;
- tilted finite-state operators and the generalized Doob generator;
- exact first-passage equations and killed-generator quasi-stationarity;
- the stoichiometric boundary map, conservation laws, and reaction cycles;
- an optional spatial lattice lift and reaction terms for the reaction–diffusion PDE.

## 5. Tilt and sampling

For edge observable increment `gxy`, the tilted operator is

```text
(Lk)xy = Qxy exp(k gxy), x≠y.
```

It is not a stochastic generator. In code, `TiltedOperator` deliberately does not implement `StochasticGenerator`, so it cannot be passed to `GillespieSimulator`.

After a positive principal right eigenvector `r` and eigenvalue `λ` have passed a residual test, the driven rates are

```text
Qk,driven(x,y) = (Lk)xy r(y)/r(x), x≠y,
Qk,driven(x,x) = -Σy≠x Qk,driven(x,y).
```

Only this `DrivenGenerator` is simulable.

## 6. Thermodynamic bookkeeping without ideal-gas statistics

For state weight `w(x)=exp(−βF(x))` and a declared reservoir/activity factor `R(x,y)`, local detailed balance is audited as the exact multiplicative identity

```text
q(x,y)/q(y,x) = [w(y)/w(x)] R(x,y),
R(y,x) = 1/R(x,y).
```

Activities are represented as `a/a°=γc/c°`, so the dimensionless chemical-potential increment is the formal logarithm `log(a/a°)`. No ideal Bose/Fermi gas, equation of state, virial expansion, or Doi–Peliti interpretation is assumed. Physical values of `F`, `γ`, and the reservoir factors are inputs requiring identification; the exact audit does not invent them.

## 7. Nuclear-quantum input is upstream of the CTMC

For a selected nuclear mass `m` and temperature `T`, the UI evaluates the thermal de Broglie convention

```text
λth = h / sqrt(2π m kB T).
```

This is a diagnostic length scale, not a binding free energy. The code has no map `λth → binding percentage`. A system-specific path-integral/quantum-chemistry or experimentally identified calculation must separately supply

```text
ΔΔGbind = ΔGbind,quantum − ΔGbind,classical,
ΔΔG‡    = ΔG‡quantum − ΔG‡classical.
```

Only then does `QuantumBindingBridge` expose the declared projection

```text
Kquantum / Kclassical             = exp(−ΔΔGbind / RT),
kforward,quantum / kforward,classical = exp(−ΔΔG‡ / RT),
kreverse,quantum / kreverse,classical =
    (kforward,quantum / kforward,classical) / (Kquantum / Kclassical).
```

The last relation is the consistency condition `K=kforward/kreverse`. Applying those floating results to the exact generator is a separate, visible rationalization/calibration step. It requires named reaction IDs and provenance. This separation matters because zero-point motion, tunnelling, and competing low/high-frequency modes can strengthen or weaken binding; `λth` does not determine the sign.

Primary anchors: Fang et al., *J. Phys. Chem. Lett.* 7 (2016), DOI `10.1021/acs.jpclett.6b00777`; Raugei & Klein, *JACS* 125 (2003), DOI `10.1021/ja0351995`. Physical constants use the 2022 CODATA values exposed by NIST.

## 8. Exponential generator, action, and quasipotential

For the linear exponential test `f_p(x)=p·x`, the finite nonlinear generator is

```text
H_N f_p(x)
  = N⁻¹ exp(−N f_p(x)) L_N exp(N f_p)(x)
  = Σr [qᶰr(Nx)/N] [exp(p·νr)−1].
```

This converges to the jump Hamiltonian under the declared density scaling. No Fourier transform is required. The Lagrangian is the Legendre–Fenchel dual, and a path has action

```text
I_T[φ] = ∫₀ᵀ L(φ(t), φ̇(t)) dt.
```

The quasipotential `V(a,b)` is the infimum of this action over both paths and travel times. `FixedTimeMinimumActionSolver` returns only a residual-bearing, fixed-mesh stationary candidate; sample-path LDP, coercivity, global minimization, and metastable scale separation remain separate analytical gates.

## 9. Spatial and chain-complex layers

The well-mixed `N→∞` limit and a hydrodynamic limit are different constructions. `PeriodicSpatialLattice` first creates a microscopic lattice generator by copying the same local reaction table to each site and adding bidirectional hopping at rate `D L²`. Only after spatial-generator, `L,K` scaling, local-equilibrium/replacement, and tightness witnesses are supplied may the hydrodynamic theorem object be constructed.

The reaction chain complex uses

```text
C₁(reactions) --∂₁=S--> C₀(species) --∂₀=0--> 0.
```

The left kernel of `S` gives conservation laws and the right kernel gives reaction cycles, both with exact rationals. The generic simplex implementation also checks the nontrivial identity `∂²=0`; Kotlin implements these objects but is not itself a chain group or a face map.

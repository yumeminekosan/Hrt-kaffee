# AR model equations and conventions

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

## 7. Exponential generator, action, and quasipotential

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

## 8. Spatial and chain-complex layers

The well-mixed `N→∞` limit and a hydrodynamic limit are different constructions. `PeriodicSpatialLattice` first creates a microscopic lattice generator by copying the same local reaction table to each site and adding bidirectional hopping at rate `D L²`. Only after spatial-generator, `L,K` scaling, local-equilibrium/replacement, and tightness witnesses are supplied may the hydrodynamic theorem object be constructed.

The reaction chain complex uses

```text
C₁(reactions) --∂₁=S--> C₀(species) --∂₀=0--> 0.
```

The left kernel of `S` gives conservation laws and the right kernel gives reaction cycles, both with exact rationals. The generic simplex implementation also checks the nontrivial identity `∂²=0`; Kotlin implements these objects but is not itself a chain group or a face map.

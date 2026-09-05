# Multi-chain Bayesian PK audit

This document defines the deployed Bayesian module. It is an exploratory parameter-identification tool conditional on the selected route model, dosing history, priors and a fixed observation-error model. It is not a validated therapeutic-drug-monitoring service and cannot produce an individual dose recommendation.

## Target distribution

Observed times are hours from the first modelled dose. Predictions use the same route-aware exact one-compartment superposition as the main simulator; observations are never rounded onto an Euler grid. For positive parameters,

\[
x_{CL}=\log CL,\quad x_V=\log V,\quad x_{k_a}=\log k_a,
\]

and bioavailability is sampled as

\[
x_F=\operatorname{logit}(F),\qquad F=(1+e^{-x_F})^{-1}.
\]

`CL`, `V` and `k_a` priors are normal in log space. A displayed prior value is the natural-scale median and its CV is converted by `ω=sqrt(log(1+CV²))`. `F` has a logistic-normal prior with a displayed median and explicit logit-scale SD. Consequently every posterior draw satisfies `CL,V,k_a>0` and `0<F<1`; rejection at an artificial zero boundary is not part of the sampler.

The observation model is supplied by the user rather than hard-coded:

\[
\log y_j\mid\theta\sim
\mathcal N\!\left(\log C(t_j;\theta)-\tfrac12\sigma^2,\sigma^2\right),
\qquad \sigma=\sqrt{\log(1+CV_{obs}^2)}.
\]

The `-σ²/2` term makes the natural-scale conditional mean equal to the latent concentration. The likelihood includes its normalizing terms. A positive concentration at a time where the route model predicts exactly zero has zero likelihood instead of being rescued by an arbitrary floor.

## Sampler

The browser runs four independently seeded random-walk Metropolis-within-Gibbs chains in transformed space. Each coordinate proposal scale adapts in 50-iteration windows toward a 0.44 one-dimensional acceptance target during warmup. Adaptation stops before retained draws. Chain seeds, retained draws, per-chain acceptance and log likelihood/posterior are returned; the same master seed reproduces all chains and posterior predictive replicates.

This is still a basic random-walk sampler. Correlated or weakly identified posteriors can mix slowly. The UI does not conceal that failure: it reports and enforces the diagnostics below.

## Required diagnostics

For each of `CL`, `V`, `k_a` and `F`, the implementation returns:

- rank-normalized split `R-hat`, taking the maximum of ordinary and folded rank-normalized values;
- bulk ESS using rank-normalized split chains;
- tail ESS as the smaller ESS of the lower-5% and upper-5% indicator sequences;
- averaged within-chain autocorrelation at lags 1, 5, 10, 20 and 50 when available;
- posterior mean, SD, interpolated median/95% credible interval and `MCSE(mean)=SD/sqrt(bulk ESS)`.

ESS uses the multi-chain autocorrelation estimator and Geyer initial-positive, initial-monotone paired sequence. The method follows [Vehtari et al., *Bayesian Analysis* 2021](https://doi.org/10.1214/20-BA1221) and the [Stan R-hat/ESS reference](https://mc-stan.org/rstan/reference/Rhat.html). The hard convergence gate is:

\[
\max \widehat R\le 1.05,\qquad
\min ESS_{bulk}\ge100M,\qquad
\min ESS_{tail}\ge100M,
\]

where `M` is the number of chains (therefore 400 for the deployed four-chain run). Extreme per-chain acceptance or no more observations than free PK parameters also blocks convergence status.

These diagnostics can identify non-mixing; they cannot prove structural identifiability. In particular, sparse single-route concentration data may leave `F`, `V` and `CL` strongly confounded.

## Posterior predictive check

Posterior draws generate replicated measurements through the same log-normal error model. At every observed time the UI reports a predictive median and 95% interval internally, then summarizes:

- fraction of observations inside their pointwise 95% predictive intervals;
- RMSE between observations and predictive medians, in the selected drug's concentration unit;
- a posterior-predictive discrepancy probability `P(T(y_rep,θ)≥T(y,θ))` based on standardized log residuals.

The apply gate additionally requires the discrepancy probability to lie in `[0.05,0.95]`. This probability is a model-check diagnostic, not the probability that the PK model is true; coverage with a handful of observations is also not external validation. The foundational replicated-data interpretation follows [Gelman, Meng and Stern (1996)](https://www.cs.princeton.edu/courses/archive/fall09/cos597A/papers/GelmanMengStern1996.pdf).

## Fail-closed behavior

Posterior summaries may be inspected when a chain or predictive diagnostic fails, because seeing the failure is useful. The **Apply exploratory inputs** button remains disabled unless every parameter passes `R-hat` and bulk/tail ESS, the sparse-data and acceptance warnings are clear, and the posterior-predictive probability is non-extreme.

Even after that computational gate passes, application only copies posterior medians into the research simulator. It does not establish route-model adequacy, correct dosing history, assay comparability, adherence, covariate effects, safety, clinical efficacy or individualized dosing validity. Those require an externally validated PopPK/TDM design; the [FDA Population Pharmacokinetics guidance](https://www.fda.gov/regulatory-information/search-fda-guidance-documents/population-pharmacokinetics) is the governing fit-for-purpose reference used by this audit.

## Machine gates

The JavaScript suite checks transformed-domain bounds, four retained chains, seed reproducibility, rank/folded `R-hat`, bulk/tail ESS, autocorrelation, posterior summaries, posterior-predictive interval ordering, explicit observation CV, removal of `Math.random`/hard-coded `sigma=10`, and the disabled-until-green UI path. The 107-question gate is executed in the same CI job, so MCMC tests alone cannot make the job green.

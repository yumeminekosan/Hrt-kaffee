package dev.hrtkaffee.ar.web

import dev.hrtkaffee.ar.web.model.ArEquilibriumParameters
import dev.hrtkaffee.ar.web.model.ArIntervention
import dev.hrtkaffee.ar.web.model.ArSuppressionModel
import dev.hrtkaffee.ar.web.model.BasisPoints
import dev.hrtkaffee.ar.web.model.FinasterideCurvePoint
import dev.hrtkaffee.ar.web.model.FinasterideKineticModel
import dev.hrtkaffee.ar.web.model.FinasterideRegimen
import dev.hrtkaffee.ar.web.model.MolecularBindingTheoryMap
import dev.hrtkaffee.ar.web.model.Rational
import dev.hrtkaffee.ar.web.model.TheoryNodeId
import dev.hrtkaffee.ar.web.model.TheoryStageStatus
import dev.hrtkaffee.ar.web.model.WebThermalDeBroglie
import kotlinx.browser.document
import org.w3c.dom.HTMLElement
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Exact Kotlin/Wasm DOM renderer for browsers where WebGL2 is unavailable.
 *
 * Compose Multiplatform's browser canvas currently requires WebGL2 through Skiko. The fallback
 * keeps the audited Kotlin models and all interactive calculations live instead of leaving an
 * empty page on software-rendered, enterprise-locked, or accessibility-test browsers.
 */
internal fun renderDomFallback(root: HTMLElement) {
    root.innerHTML = """
        <style>${fallbackStyles()}</style>
        <main id="dom-fallback" class="fallback-root">
          <div class="noise"></div>
          <header class="hero">
            <div>
              <p class="eyebrow">K/5AR // FINASTERIDE KINETICS</p>
              <h1>非那雄胺 · 结合占有与 DHT 时间曲线</h1>
              <p class="lede">输入每日剂量与观察天数；计算每日口服后的动态结果。</p>
            </div>
            <div class="runtime-stack">
              <span class="tag cyan">KOTLIN/WASM · LIVE</span>
              <span class="tag violet">DOM SAFE MODE</span>
              <small>WebGL2 unavailable · Kotlin model retained</small>
            </div>
          </header>

          <section class="panel fin-panel">
            <div class="panel-kicker">01 · INPUT / REGIMEN</div>
            <div class="section-head">
              <div>
                <h2>每日口服方案</h2>
                <p>给药间隔固定为 24 h。</p>
              </div>
              <span class="tag green">MODEL ONLINE</span>
            </div>

            <div class="preset-row">
              <button id="preset-02" type="button">0.2 mg</button>
              <button id="preset-1" type="button">1 mg</button>
              <button id="preset-5" type="button">5 mg</button>
              <button id="preset-15" class="active" type="button">15 mg</button>
            </div>

            <div class="fin-operations-grid">
              <div class="control-deck">
                <label for="dose-slider">
                  <span><b>DOSE</b> 每日口服剂量</span><strong id="dose-value">15.00 mg/day</strong>
                  <input id="dose-slider" type="range" min="0" max="2000" value="1500" />
                </label>
                <label for="days-slider">
                  <span><b>TIME</b> 连续观察</span><strong id="days-value">14 days</strong>
                  <input id="days-slider" type="range" min="1" max="42" value="14" />
                </label>
              </div>

              <div class="signal-core">
                <span>ENDPOINT DHT SUPPRESSION</span>
                <strong id="dht-final">—</strong>
                <small>serum DHT at selected horizon</small>
                <div class="signal-bar"><i id="signal-bar"></i></div>
                <p>峰值抑制 <b id="dht-peak">—</b></p>
              </div>

              <div class="metric-deck">
                <div><span>5αR2 BOUND</span><b id="type2-bound">—</b></div>
                <div><span>5αR1 INHIBITED</span><b id="type1-inhibited">—</b></div>
                <div><span>FREE FINASTERIDE</span><b id="fin-concentration">—</b></div>
              </div>
            </div>

            <div class="curve-head">
              <b>动态曲线 / TIME COURSE</b>
              <div><span class="curve-key cyan-key"></span>DHT 抑制 <span class="curve-key amber-key"></span>5αR2 占有 <span class="curve-key coral-key"></span>5αR1 抑制</div>
            </div>
            <div id="curve-host" class="curve-host"></div>

            <aside id="fin-boundary" class="model-boundary extrapolated">
              <b id="fin-boundary-label">EXTRAPOLATED DOSE</b>
              <span id="fin-boundary-text">每日剂量高于 5 mg：曲线是模型外推，不表示额外临床收益；非那雄胺也不是直接 AR 拮抗剂。</span>
            </aside>
            <p class="fin-source">参数：Suzuki et al. 2010, DOI 10.2133/dmpk.25.208 · 结构：PDB 7BW1 · 仅供研究模拟</p>
          </section>
        </main>
    """.trimIndent()

    bindFinasterideControls()
}

private fun nodeCard(id: TheoryNodeId, extraClass: String = ""): String {
    val node = MolecularBindingTheoryMap.node(id)
    return """
        <article class="flow-node ${statusClass(node.status)} $extraClass">
          <span class="node-code">${node.code}</span>
          <div><h3>${node.title}</h3><p>${node.detail}</p><small>${statusLabel(node.status)}</small></div>
        </article>
    """.trimIndent()
}

private fun lane(title: String, ids: List<TheoryNodeId>): String = """
    <div class="flow-lane">
      <h4>$title</h4>
      ${ids.map { id -> nodeCard(id) }.joinToString("<div class=\"down-arrow\">↓</div>")}
    </div>
""".trimIndent()

private fun arrow(label: String): String = """<div class="inline-arrow"><b>→</b><small>$label</small></div>"""

private fun statusClass(status: TheoryStageStatus): String = when (status) {
    TheoryStageStatus.EXACT_CORE -> "exact"
    TheoryStageStatus.CONDITIONAL_THEOREM -> "gated"
    TheoryStageStatus.NUMERICAL_CERTIFICATE -> "numerical"
    TheoryStageStatus.INPUT_REQUIRED -> "input-open"
    TheoryStageStatus.LIVE_PROJECTION -> "live"
}

private fun statusLabel(status: TheoryStageStatus): String = when (status) {
    TheoryStageStatus.EXACT_CORE -> "IMPLEMENTED · EXACT STRUCTURE"
    TheoryStageStatus.CONDITIONAL_THEOREM -> "THEOREM · NAMED GATES REQUIRED"
    TheoryStageStatus.NUMERICAL_CERTIFICATE -> "NUMERICAL · RESIDUAL REQUIRED"
    TheoryStageStatus.INPUT_REQUIRED -> "PHYSICAL INPUT REQUIRED"
    TheoryStageStatus.LIVE_PROJECTION -> "LIVE · REDUCED MODEL ONLY"
}

private fun bindQuantumControls() {
    val mass = input("mass-slider")
    val temperature = input("temperature-slider")

    fun update() {
        val massValue = mass.value.toInt().coerceIn(1, 128)
        val temperatureValue = temperature.value.toInt().coerceIn(20, 400)
        val scale = WebThermalDeBroglie.evaluate(massValue.toDouble(), temperatureValue.toDouble())
        text("mass-value", massValue.toString())
        text("temperature-value", temperatureValue.toString())
        text("lambda-value", "${decimal(scale.wavelengthPicometres, 1)} pm")
    }

    mass.addEventListener("input", { _: Event -> update() })
    temperature.addEventListener("input", { _: Event -> update() })
    update()
}

private fun bindArControls() {
    val model = ArSuppressionModel(ArEquilibriumParameters.illustrative())
    val direct = input("direct-slider")
    val upstream = input("upstream-slider")
    val presets = listOf(
        Triple("preset-control", 0, 0),
        Triple("preset-balanced", 4_200, 5_800),
        Triple("preset-direct", 7_600, 3_600),
    )

    fun update() {
        val directValue = direct.value.toInt().coerceIn(0, 10_000)
        val upstreamValue = upstream.value.toInt().coerceIn(0, 10_000)
        val result = model.evaluate(
            ArIntervention(BasisPoints(directValue), BasisPoints(upstreamValue)),
        )
        val worlds = result.counterfactuals
        val control = worlds.control.toDouble()
        val relative = result.signalRelativeToControl.toDouble()

        text("direct-value", basisPointsPercent(directValue))
        text("upstream-value", basisPointsPercent(upstreamValue))
        exactText("signal-relative", percent(relative), result.signalRelativeToControl)
        text("suppression-total", percent(1.0 - relative))
        exactText("direct-shapley", signedPercent(result.directShapleyContribution.toDouble()), result.directShapleyContribution)
        exactText("upstream-shapley", signedPercent(result.upstreamShapleyContribution.toDouble()), result.upstreamShapleyContribution)
        exactText("non-additivity", signedPercent(result.nonAdditivity.toDouble()), result.nonAdditivity)
        exactText("world-control", percent(worlds.control.toDouble() / control), worlds.control)
        exactText("world-direct", percent(worlds.directOnly.toDouble() / control), worlds.directOnly)
        exactText("world-upstream", percent(worlds.upstreamOnly.toDouble() / control), worlds.upstreamOnly)
        exactText("world-combined", percent(worlds.combined.toDouble() / control), worlds.combined)
        element("signal-bar").style.width = "${(relative * 100.0).coerceIn(0.0, 100.0)}%"

        presets.forEach { (id, presetDirect, presetUpstream) ->
            element(id).className = if (directValue == presetDirect && upstreamValue == presetUpstream) "active" else ""
        }
    }

    direct.addEventListener("input", { _: Event -> update() })
    upstream.addEventListener("input", { _: Event -> update() })
    presets.forEach { (id, presetDirect, presetUpstream) ->
        element(id).addEventListener("click", { _: Event ->
            direct.value = presetDirect.toString()
            upstream.value = presetUpstream.toString()
            update()
        })
    }
    update()
}

private fun bindFinasterideControls() {
    val model = FinasterideKineticModel()
    val dose = input("dose-slider")
    val days = input("days-slider")
    val presets = listOf(
        "preset-02" to 20,
        "preset-1" to 100,
        "preset-5" to 500,
        "preset-15" to 1_500,
    )

    fun update() {
        val doseCentiMg = dose.value.toInt().coerceIn(0, 2_000)
        val dayCount = days.value.toInt().coerceIn(1, 42)
        val result = model.simulate(
            FinasterideRegimen(dailyDoseMg = doseCentiMg / 100.0, days = dayCount),
        )
        val final = result.finalPoint
        val finalSuppression = final.serumDhtSuppressionFraction

        text("dose-value", "${decimal(doseCentiMg / 100.0, 2)} mg/day")
        text("days-value", "$dayCount days")
        text("dht-final", percent(finalSuppression))
        text("dht-peak", percent(result.peakDhtSuppressionFraction))
        text("type2-bound", percent(final.type2OccupancyFraction))
        text("type1-inhibited", percent(final.type1InhibitionFraction))
        text("fin-concentration", "${decimal(final.plasmaConcentrationNm, 1)} nM")
        element("signal-bar").style.width = "${(finalSuppression * 100.0).coerceIn(0.0, 100.0)}%"
        element("curve-host").innerHTML = curveSvg(result.curve, dayCount)

        val inDomain = result.regimen.isRepeatedDoseReferenceDomain
        element("fin-boundary").className = if (inDomain) "model-boundary" else "model-boundary extrapolated"
        text("fin-boundary-label", if (inDomain) "POPULATION MODEL" else "EXTRAPOLATED DOSE")
        text(
            "fin-boundary-text",
            if (inDomain) {
                "非那雄胺作用于 5α-还原酶并降低 T→DHT 通量；它不是直接 AR 拮抗剂。"
            } else {
                "每日剂量高于 5 mg：曲线是模型外推，不表示额外临床收益；非那雄胺也不是直接 AR 拮抗剂。"
            },
        )

        presets.forEach { (id, presetDose) ->
            element(id).className = if (doseCentiMg == presetDose) "active" else ""
        }
    }

    dose.addEventListener("input", { _: Event -> update() })
    days.addEventListener("input", { _: Event -> update() })
    presets.forEach { (id, presetDose) ->
        element(id).addEventListener("click", { _: Event ->
            dose.value = presetDose.toString()
            update()
        })
    }
    update()
}

private fun curveSvg(points: List<FinasterideCurvePoint>, days: Int): String {
    val width = 1_000.0
    val height = 300.0
    val maximumTime = points.lastOrNull()?.timeHours?.coerceAtLeast(1.0) ?: 1.0

    fun polyline(value: (FinasterideCurvePoint) -> Double): String = points.joinToString(" ") { point ->
        val x = point.timeHours / maximumTime * width
        val y = (1.0 - value(point).coerceIn(0.0, 1.0)) * height
        "${decimal(x, 1)},${decimal(y, 1)}"
    }

    return """
        <svg viewBox="0 0 1000 330" role="img" aria-label="Finasteride inhibition time curves">
          <g class="curve-grid">
            <path d="M0 0H1000 M0 75H1000 M0 150H1000 M0 225H1000 M0 300H1000" />
            <path d="M0 0V300 M250 0V300 M500 0V300 M750 0V300 M1000 0V300" />
          </g>
          <polyline class="curve-line dht-line" points="${polyline { it.serumDhtSuppressionFraction }}" />
          <polyline class="curve-line type2-line" points="${polyline { it.type2OccupancyFraction }}" />
          <polyline class="curve-line type1-line" points="${polyline { it.type1InhibitionFraction }}" />
          <text x="0" y="326">DAY 0</text><text x="920" y="326">DAY $days</text>
        </svg>
    """.trimIndent()
}

private fun input(id: String): HTMLInputElement =
    document.getElementById(id) as? HTMLInputElement ?: error("Missing input #$id")

private fun element(id: String): HTMLElement =
    document.getElementById(id) as? HTMLElement ?: error("Missing element #$id")

private fun text(id: String, value: String) {
    element(id).textContent = value
}

private fun exactText(id: String, value: String, exact: Rational) {
    element(id).apply {
        textContent = value
        title = "exact: $exact"
    }
}

private fun basisPointsPercent(value: Int): String = "${decimal(value / 100.0, 1)}%"

private fun percent(value: Double): String = "${decimal(value * 100.0, 1)}%"

private fun signedPercent(value: Double): String {
    val sign = if (value > 0.0) "+" else ""
    return "$sign${percent(value)}"
}

private fun decimal(value: Double, digits: Int): String {
    val scale = when (digits) {
        2 -> 100L
        1 -> 10L
        else -> 1L
    }
    val scaled = (abs(value) * scale).roundToLong()
    val sign = if (value < 0.0) "−" else ""
    val whole = scaled / scale
    return if (digits == 0) "$sign$whole" else "$sign$whole.${(scaled % scale).toString().padStart(digits, '0')}"
}

private fun fallbackStyles(): String = """
    @font-face {
      font-family: "Terra Ops CJK";
      src: url("fonts/terra-ops-cjk.ttf") format("truetype");
      font-style: normal;
      font-weight: 100 900;
      font-display: swap;
    }
    :root { color-scheme: dark; }
    body { overflow: hidden !important; }
    .fallback-root {
      --bg: #080b0f; --panel: #10171d; --raised: #172129; --grid: #2a3842;
      --cyan: #54e6e0; --amber: #ffc857; --coral: #ff6b5f; --violet: #c792ea;
      --green: #8fe388; --text: #f4f7f7; --muted: #97a7b0;
      --ops-font: "Terra Ops CJK", "Noto Sans SC", sans-serif;
      position: relative; height: 100dvh; overflow: auto; padding: 28px 34px 54px;
      color: var(--text); background:
        radial-gradient(circle at 82% 4%, rgba(84,230,224,.12), transparent 34rem),
        radial-gradient(circle at 12% 32%, rgba(199,146,234,.08), transparent 28rem), var(--bg);
      font-family: var(--ops-font);
      scrollbar-color: var(--cyan) var(--bg);
    }
    .fallback-root * { box-sizing: border-box; }
    .noise { position: fixed; inset: 0; pointer-events: none; opacity: .36; background-image:
      linear-gradient(rgba(42,56,66,.15) 1px, transparent 1px),
      linear-gradient(90deg, rgba(42,56,66,.15) 1px, transparent 1px); background-size: 48px 48px; }
    .hero, .section-head { position: relative; display: flex; align-items: end; justify-content: space-between; gap: 24px; }
    .hero { max-width: 1560px; margin: 0 auto 20px; padding: 4px 2px; }
    .eyebrow, .panel-kicker, .parallel-label { margin: 0 0 6px; color: var(--cyan); font: 800 11px/1.4 var(--ops-font); letter-spacing: .18em; }
    h1, h2, h3, h4, p { margin-top: 0; }
    h1 { margin-bottom: 5px; font-size: clamp(26px, 3vw, 43px); line-height: 1.08; letter-spacing: -.03em; }
    h2 { margin-bottom: 5px; font-size: clamp(19px, 2vw, 28px); letter-spacing: -.02em; }
    .lede, .section-head p { margin-bottom: 0; color: var(--muted); }
    .runtime-stack { display: grid; justify-items: end; gap: 7px; color: var(--muted); font: 10px var(--ops-font); }
    .tag { display: inline-block; padding: 5px 8px; border: 1px solid currentColor; font: 800 9px var(--ops-font); letter-spacing: .08em; }
    .cyan { color: var(--cyan); } .amber { color: var(--amber); } .coral { color: var(--coral); } .violet { color: var(--violet); } .green { color: var(--green); }
    .panel { position: relative; max-width: 1560px; margin: 0 auto 20px; padding: 22px; border: 1px solid var(--grid); background: rgba(16,23,29,.96); clip-path: polygon(0 0, calc(100% - 28px) 0, 100% 28px, 100% 100%, 18px 100%, 0 calc(100% - 18px)); }
    .panel::before { content: ""; position: absolute; inset: 0 0 auto; height: 3px; background: linear-gradient(90deg, var(--violet), var(--cyan), transparent 72%); }
    .panel-kicker { color: var(--violet); }
    .legend { display: flex; flex-wrap: wrap; justify-content: end; gap: 6px; }
    .telemetry-grid { display: grid; grid-template-columns: 1fr 1fr .78fr; gap: 12px; margin: 18px 0 14px; }
    .slider-card, .lambda-card { display: grid; grid-template-columns: 1fr auto; gap: 8px 12px; padding: 13px; border: 1px solid var(--grid); background: var(--raised); }
    .slider-card span, .lambda-card span { color: var(--muted); font: 700 10px var(--ops-font); letter-spacing: .06em; }
    .slider-card strong { color: var(--violet); font: 800 15px var(--ops-font); }
    input[type=range] { grid-column: 1 / -1; width: 100%; accent-color: var(--violet); cursor: pointer; }
    .lambda-card { align-content: center; border-color: rgba(199,146,234,.72); background: rgba(199,146,234,.08); }
    .lambda-card strong { grid-column: 1 / -1; color: var(--violet); font: 900 27px var(--ops-font); }
    .lambda-card small { color: var(--muted); font: 10px var(--ops-font); }
    .flow-chain { display: grid; grid-template-columns: minmax(0,1fr) auto minmax(0,1fr) auto minmax(0,1fr); gap: 9px; align-items: center; }
    .flow-node { display: flex; min-width: 0; gap: 10px; padding: 12px; border: 1px solid var(--node); background: var(--raised); clip-path: polygon(0 0, calc(100% - 14px) 0, 100% 14px, 100% 100%, 8px 100%, 0 calc(100% - 8px)); }
    .flow-node.exact { --node: rgba(84,230,224,.62); --accent: var(--cyan); }
    .flow-node.gated { --node: rgba(255,200,87,.62); --accent: var(--amber); }
    .flow-node.numerical { --node: rgba(199,146,234,.7); --accent: var(--violet); }
    .flow-node.input-open { --node: rgba(255,107,95,.72); --accent: var(--coral); }
    .flow-node.live { --node: rgba(143,227,136,.62); --accent: var(--green); }
    .node-code { flex: 0 0 33px; height: 25px; display: grid; place-items: center; color: var(--bg); background: var(--accent); font: 900 10px var(--ops-font); }
    .flow-node h3 { margin-bottom: 4px; font-size: 13px; }
    .flow-node p { min-height: 25px; margin-bottom: 7px; color: var(--muted); font-size: 10px; line-height: 1.35; }
    .flow-node small { color: var(--accent); font: 800 8px var(--ops-font); letter-spacing: .04em; }
    .inline-arrow { width: 75px; text-align: center; color: var(--violet); }
    .inline-arrow b { display: block; font-size: 23px; }.inline-arrow small { color: var(--muted); font: 8px var(--ops-font); }
    .major-arrow, .down-arrow { padding: 8px; color: var(--violet); text-align: center; font: 800 9px var(--ops-font); letter-spacing: .06em; }
    .node-wide { width: 100%; }
    .lane-grid, .parallel-grid { display: grid; grid-template-columns: .8fr 1.25fr 1.15fr; gap: 12px; margin-top: 12px; }
    .flow-lane { padding: 10px; border: 1px solid rgba(42,56,66,.85); background: rgba(0,0,0,.13); }
    .flow-lane h4 { color: var(--muted); font: 800 9px var(--ops-font); letter-spacing: .08em; }
    .parallel-label { margin-top: 18px; color: var(--muted); }
    .parallel-grid { grid-template-columns: repeat(3,1fr); margin-top: 0; }
    .quantum-boundary, .model-boundary { display: flex; gap: 12px; margin-top: 14px; padding: 13px; border: 1px solid rgba(255,107,95,.65); background: rgba(255,107,95,.07); }
    .quantum-boundary > strong { color: var(--coral); font: 900 18px var(--ops-font); }
    .quantum-boundary b { font-size: 12px; }.quantum-boundary p { margin: 4px 0 0; color: var(--muted); font-size: 11px; line-height: 1.45; }
    .reference-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 8px; margin-top: 12px; }
    .reference { display: grid; gap: 3px; padding: 9px; border: 1px solid var(--grid); font-size: 9px; }.reference b { color: var(--cyan); }.reference code { color: var(--muted); font-size: 8px; }
    .ar-panel::before { background: linear-gradient(90deg, var(--cyan), var(--amber), transparent 72%); }
    .preset-row { display: grid; grid-template-columns: repeat(3,1fr); gap: 10px; margin: 17px 0 14px; }
    button { padding: 10px; border: 1px solid var(--grid); color: var(--muted); background: var(--raised); font: 800 10px var(--ops-font); cursor: pointer; }
    button.active, button:hover { border-color: var(--cyan); color: var(--cyan); background: rgba(84,230,224,.1); }
    .operations-grid { display: grid; grid-template-columns: 1.1fr .72fr .78fr; gap: 14px; }
    .control-deck, .signal-core, .metric-deck { padding: 16px; border: 1px solid var(--grid); background: var(--raised); }
    .control-deck label { display: grid; grid-template-columns: 1fr auto; gap: 6px 12px; padding: 10px 0; }.control-deck label + label { border-top: 1px solid var(--grid); }
    .control-deck span { font-size: 14px; }.control-deck span b { display: inline-grid; place-items: center; width: 42px; height: 28px; margin-right: 8px; color: var(--bg); background: var(--cyan); font-size: 10px; }
    .control-deck label:nth-child(2) span b { background: var(--amber); }.control-deck strong { color: var(--cyan); font: 900 20px var(--ops-font); }.control-deck label:nth-child(2) strong { color: var(--amber); }
    .control-deck small { grid-column: 1 / -1; color: var(--muted); }
    .signal-core { display: grid; align-content: center; text-align: center; }.signal-core > span { color: var(--muted); font: 800 10px var(--ops-font); }.signal-core > strong { color: var(--cyan); font: 900 42px var(--ops-font); }.signal-core > small { color: var(--muted); }
    .signal-bar { height: 7px; margin: 16px 0 8px; background: var(--grid); }.signal-bar i { display: block; height: 100%; background: linear-gradient(90deg,var(--coral),var(--amber),var(--cyan)); transition: width .2s ease; }
    .signal-core p { margin: 0; color: var(--muted); }.signal-core p b { color: var(--coral); }
    .metric-deck { display: grid; gap: 9px; }.metric-deck div { display: flex; align-items: center; justify-content: space-between; padding-bottom: 9px; border-bottom: 1px solid var(--grid); }.metric-deck div:last-child { border: 0; }.metric-deck span { color: var(--muted); font: 800 9px var(--ops-font); }.metric-deck b { color: var(--violet); font: 900 18px var(--ops-font); }
    .world-grid { display: grid; grid-template-columns: repeat(4,1fr); gap: 10px; margin-top: 14px; }.world-grid div { display: grid; gap: 5px; padding: 13px; border: 1px solid var(--grid); background: rgba(0,0,0,.12); }.world-grid span { color: var(--muted); font: 800 9px var(--ops-font); }.world-grid b { color: var(--cyan); font: 900 22px var(--ops-font); }.world-grid small { color: var(--muted); }
    .model-boundary { border-color: var(--grid); background: rgba(0,0,0,.15); }.model-boundary b { color: var(--coral); font: 900 10px var(--ops-font); }.model-boundary span { color: var(--muted); font-size: 10px; line-height: 1.45; }
    .fin-panel::before { background: linear-gradient(90deg, var(--cyan), var(--amber), transparent 72%); }
    .fin-panel .preset-row { grid-template-columns: repeat(4,1fr); }
    .fin-operations-grid { display: grid; grid-template-columns: 1.05fr .72fr .82fr; gap: 14px; }
    .curve-head { display: flex; justify-content: space-between; gap: 18px; margin: 20px 0 8px; color: var(--muted); font: 800 10px var(--ops-font); }
    .curve-head > div { display: flex; flex-wrap: wrap; align-items: center; gap: 7px; }
    .curve-key { width: 18px; height: 2px; display: inline-block; margin-left: 8px; }.cyan-key { background: var(--cyan); }.amber-key { background: var(--amber); }.coral-key { background: var(--coral); }
    .curve-host { min-height: 310px; padding: 10px; border: 1px solid var(--grid); background: var(--bg); }
    .curve-host svg { display: block; width: 100%; height: auto; overflow: visible; }
    .curve-grid path { fill: none; stroke: rgba(42,56,66,.8); stroke-width: 1; }
    .curve-line { fill: none; stroke-linecap: round; stroke-linejoin: round; }.dht-line { stroke: var(--cyan); stroke-width: 4; }.type2-line { stroke: var(--amber); stroke-width: 3; }.type1-line { stroke: var(--coral); stroke-width: 3; }
    .curve-host text { fill: var(--muted); font: 10px var(--ops-font); }
    .model-boundary.extrapolated { border-color: rgba(255,107,95,.65); background: rgba(255,107,95,.07); }
    .fin-source { margin: 10px 0 0; color: var(--muted); font-size: 9px; }
    @media (max-width: 1100px) { .lane-grid, .parallel-grid, .operations-grid, .fin-operations-grid { grid-template-columns: 1fr; }.flow-lane { display: grid; gap: 6px; }.reference-grid { grid-template-columns: repeat(2,1fr); } }
    @media (max-width: 760px) {
      .fallback-root { padding: 18px 14px 40px; }.hero, .section-head { align-items: start; flex-direction: column; }.runtime-stack { justify-items: start; }.panel { padding: 17px; }
      .telemetry-grid, .preset-row, .world-grid, .reference-grid { grid-template-columns: 1fr; }.flow-chain { grid-template-columns: 1fr; }.inline-arrow { width: 100%; }.inline-arrow b { transform: rotate(90deg); }.quantum-chain .flow-node { width: 100%; }
      .legend { justify-content: start; }.signal-core > strong { font-size: 34px; }
    }
""".trimIndent()

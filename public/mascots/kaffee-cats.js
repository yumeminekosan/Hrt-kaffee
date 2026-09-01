(function () {
    'use strict';

    const CAT_CONFIG = {
        presets: { slug: 'presets', label: '预设三花猫' },
        'five-ar': { slug: 'five-ar', label: '5αR 银虎斑猫' },
        bicalutamide: { slug: 'bicalutamide', label: '比卡鲁胺紫灰猫' },
        'transdermal-e2': { slug: 'transdermal-e2', label: '透皮雌二醇奶牛猫' },
        'drug-settings': { slug: 'drug-settings', label: '药物设置橘猫' },
        'model-selection': { slug: 'model-selection', label: '模型选择暹罗猫' },
        bayesian: { slug: 'bayesian', label: '贝叶斯黑猫' },
        progestogen: { slug: 'progestogen', label: '孕激素布偶猫' },
        interactions: { slug: 'interactions', label: '相互作用玳瑁猫' },
        'monte-carlo': { slug: 'monte-carlo', label: '蒙特卡洛白猫' },
        'pk-parameters': { slug: 'pk-parameters', label: 'PK 参数棕虎斑猫' },
        'simulation-controls': { slug: 'simulation-controls', label: '运行控制奶牛猫' },
        results: { slug: 'results', label: '结果状态缅因猫' },
        'stats-summary': { slug: 'stats-summary', label: '统计摘要灰猫' },
        'time-curves': { slug: 'time-curves', label: '时间曲线蓝猫' },
        'event-log': { slug: 'event-log', label: '事件日志黑白猫' },
        'research-footer': { slug: 'research-footer', label: '研究说明白长毛猫' }
    };

    const cats = new Map();
    const resetTimers = new WeakMap();
    const persistentStates = new Set(['busy', 'sleepy']);

    function assetPath(slug, layer) {
        return `mascots/cats/${slug}/${layer}.png`;
    }

    function catMarkup(config) {
        return [
            ['body', 'kaffee-cat-body'],
            ['tail', 'kaffee-cat-tail'],
            ['head', 'kaffee-cat-head']
        ].map(([layer, className]) => (
            `<img class="kaffee-cat-layer ${className}" src="${assetPath(config.slug, layer)}" alt="" aria-hidden="true" draggable="false">`
        )).join('') + '<span class="kaffee-cat-emote" aria-hidden="true"></span>';
    }

    function makeCat(moduleName) {
        const config = CAT_CONFIG[moduleName];
        if (!config) return null;

        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'kaffee-cat';
        button.dataset.state = 'idle';
        button.dataset.module = moduleName;
        button.setAttribute('aria-label', `${config.label}，点击摸摸它`);
        button.title = `${config.label} · 点击摸摸`;
        button.innerHTML = catMarkup(config);
        button.addEventListener('click', (event) => {
            event.stopPropagation();
            setState(button, 'petted', 950);
        });
        return button;
    }

    function attachCat(host) {
        const moduleName = host.dataset.catModule;
        const cat = makeCat(moduleName);
        if (!cat) return;

        if (host.matches('.stats-grid, .chart-grid')) {
            host.classList.add('kaffee-cat-floating-host');
            cat.classList.add('kaffee-cat-floating');
            host.append(cat);
            cats.set(moduleName, cat);
            return;
        }

        if (host.tagName === 'FOOTER') {
            host.classList.add('kaffee-cat-host');
            cat.classList.add('kaffee-cat-footer');
            host.append(cat);
            cats.set(moduleName, cat);
            return;
        }

        let header = host.matches('.chart-area')
            ? host.querySelector(':scope > .chart-header')
            : host.querySelector(':scope > .panel-header, :scope > .pg-header');

        if (!header) {
            header = document.createElement('div');
            header.className = 'kaffee-cat-control-row kaffee-cat-host';
            host.prepend(header);
        } else {
            header.classList.add('kaffee-cat-host');
        }

        const trailingBadge = header.querySelector(':scope > .panel-badge, :scope > .pg-badge, :scope > .status-badge');
        header.insertBefore(cat, trailingBadge || null);
        cats.set(moduleName, cat);
    }

    function setState(cat, state, duration) {
        if (!cat) return;
        const oldTimer = resetTimers.get(cat);
        if (oldTimer) window.clearTimeout(oldTimer);
        cat.dataset.state = state;

        if (duration && !persistentStates.has(state)) {
            resetTimers.set(cat, window.setTimeout(() => {
                cat.dataset.state = 'idle';
                resetTimers.delete(cat);
            }, duration));
        }
    }

    function setModuleState(moduleName, state, duration) {
        setState(cats.get(moduleName), state, duration);
    }

    function moduleFromTarget(target) {
        return target.closest('[data-cat-module]')?.dataset.catModule;
    }

    function bindModuleInteractions() {
        document.querySelectorAll('[data-cat-module]').forEach((host) => {
            host.addEventListener('focusin', (event) => {
                if (!event.target.closest('.kaffee-cat')) {
                    setModuleState(host.dataset.catModule, 'curious', 1000);
                }
            });

            host.addEventListener('input', () => {
                setModuleState(host.dataset.catModule, 'curious', 850);
            });

            host.addEventListener('change', () => {
                setModuleState(host.dataset.catModule, 'happy', 850);
            });

            host.addEventListener('invalid', () => {
                setModuleState(host.dataset.catModule, 'error', 1200);
            }, true);

            host.addEventListener('pointerdown', (event) => {
                if (event.target.closest('button, .model-chip') && !event.target.closest('.kaffee-cat')) {
                    setModuleState(host.dataset.catModule, 'pounce', 520);
                }
            });

            if (host.hasAttribute('aria-busy')) {
                const updateBusyState = () => {
                    if (host.getAttribute('aria-busy') === 'true') {
                        setModuleState(host.dataset.catModule, 'busy');
                    } else {
                        setModuleState(host.dataset.catModule, 'happy', 900);
                    }
                };
                new MutationObserver(updateBusyState).observe(host, {
                    attributes: true,
                    attributeFilter: ['aria-busy']
                });
                updateBusyState();
            }
        });
    }

    function bindSimulationStates() {
        const runButton = document.getElementById('runSimulation');
        const stopButton = document.getElementById('stopBtn');
        const resetButton = document.getElementById('resetBtn');
        const exportButton = document.getElementById('exportBtn');
        const statusBadge = document.getElementById('statusBadge');
        const progressFill = document.getElementById('progressFill');
        const bayesianButton = document.getElementById('bayesianRunBtn');
        const bayesianResults = document.getElementById('bayesianResults');

        runButton?.addEventListener('click', () => {
            setModuleState('simulation-controls', 'busy');
            setModuleState('results', 'busy');
            setModuleState('stats-summary', 'busy');
            setModuleState('time-curves', 'busy');
            setModuleState('event-log', 'busy');
        });
        stopButton?.addEventListener('click', () => {
            setModuleState('simulation-controls', 'error', 1000);
            setModuleState('results', 'error', 1000);
            setModuleState('stats-summary', 'error', 1000);
            setModuleState('time-curves', 'error', 1000);
            setModuleState('event-log', 'error', 1000);
        });
        resetButton?.addEventListener('click', () => {
            setModuleState('simulation-controls', 'petted', 800);
            setModuleState('results', 'sleepy');
            setModuleState('stats-summary', 'sleepy');
            setModuleState('time-curves', 'sleepy');
            window.setTimeout(() => setModuleState('results', 'idle'), 1100);
            window.setTimeout(() => setModuleState('stats-summary', 'idle'), 1100);
            window.setTimeout(() => setModuleState('time-curves', 'idle'), 1100);
        });
        exportButton?.addEventListener('click', () => {
            setModuleState('simulation-controls', 'happy', 1000);
        });
        bayesianButton?.addEventListener('click', () => {
            setModuleState('bayesian', 'busy');
        });

        if (statusBadge) {
            new MutationObserver(() => {
                const status = statusBadge.textContent.trim().toLowerCase();
                if (/running|计算|模拟/.test(status)) {
                    setModuleState('results', 'busy');
                    setModuleState('stats-summary', 'busy');
                    setModuleState('time-curves', 'busy');
                } else if (/error|fail|错误/.test(status)) {
                    setModuleState('results', 'error', 1400);
                    setModuleState('simulation-controls', 'error', 1400);
                    setModuleState('stats-summary', 'error', 1400);
                    setModuleState('time-curves', 'error', 1400);
                } else if (/complete|done|ready|完成|就绪/.test(status)) {
                    setModuleState('results', 'happy', 1200);
                    setModuleState('simulation-controls', 'happy', 1200);
                    setModuleState('stats-summary', 'happy', 1200);
                    setModuleState('time-curves', 'happy', 1200);
                }
            }).observe(statusBadge, { childList: true, subtree: true, characterData: true });
        }

        if (progressFill) {
            new MutationObserver(() => {
                const progress = Number.parseFloat(progressFill.style.width) || 0;
                if (progress > 0 && progress < 100) {
                    setModuleState('results', 'busy');
                    setModuleState('time-curves', 'busy');
                } else if (progress >= 100) {
                    setModuleState('results', 'happy', 1200);
                    setModuleState('simulation-controls', 'happy', 1200);
                    setModuleState('stats-summary', 'happy', 1200);
                    setModuleState('time-curves', 'happy', 1200);
                }
            }).observe(progressFill, { attributes: true, attributeFilter: ['style'] });
        }

        if (bayesianResults) {
            new MutationObserver(() => {
                const visible = getComputedStyle(bayesianResults).display !== 'none';
                if (visible) setModuleState('bayesian', 'happy', 1200);
            }).observe(bayesianResults, { attributes: true, attributeFilter: ['style'] });
        }
    }

    function bindResultReadouts() {
        const selectors = [
            '#targetEffectValue', '#bicSuppression', '#tdE2Total', '#pgGnrhEffect',
            '#cmaxStat', '#cminStat', '#aucStat'
        ];
        selectors.forEach((selector) => {
            const readout = document.querySelector(selector);
            if (!readout) return;
            new MutationObserver(() => {
                const moduleName = moduleFromTarget(readout) || 'results';
                if (!/—|CALCULATING|-$/.test(readout.textContent.trim())) {
                    setModuleState(moduleName, 'happy', 800);
                }
            }).observe(readout, { childList: true, subtree: true, characterData: true });
        });
    }

    function bindLogReactions() {
        const log = document.getElementById('logContainer');
        if (!log) return;
        new MutationObserver(() => {
            const content = log.textContent.toLowerCase();
            if (/error|fail|错误/.test(content)) {
                setModuleState('event-log', 'error', 1100);
            } else {
                setModuleState('event-log', 'happy', 750);
            }
        }).observe(log, { childList: true, subtree: true, characterData: true });
    }

    function installKaffeeCats() {
        document.querySelectorAll('[data-cat-module]').forEach(attachCat);
        bindModuleInteractions();
        bindSimulationStates();
        bindResultReadouts();
        bindLogReactions();
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', installKaffeeCats, { once: true });
    } else {
        installKaffeeCats();
    }
})();

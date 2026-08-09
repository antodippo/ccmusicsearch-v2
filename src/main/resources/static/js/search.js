/*
 * Search page behaviour.
 *
 * The server sends the whole ranked result set in one go, so filtering, sorting and
 * paging all happen here over the rows already in the document. Every row carries its
 * raw values as data attributes; nothing is re-fetched.
 */
(function () {
    'use strict';

    var root = document.documentElement;
    var PAGE_SIZE = 25;

    /* ── theme ─────────────────────────────────────────────────────────────── */

    var themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.addEventListener('click', function () {
            var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
            root.setAttribute('data-theme', next);
            try {
                localStorage.setItem('ccmusic-theme', next);
            } catch (e) { /* private mode: the choice just does not survive the reload */ }
        });
    }

    var rowsList = document.getElementById('rows');
    var rows = rowsList ? Array.prototype.slice.call(rowsList.children) : [];
    if (!rows.length) {
        return;
    }

    /* ── state ─────────────────────────────────────────────────────────────── */

    var sources = new Set();
    var licences = new Set();
    var commercialOnly = false;
    var sort = 'relevance';
    var shown = PAGE_SIZE;
    var ordered = rows.slice();

    var lengthRange = setupRange('length', document.getElementById('length-value'), function (lo, hi) {
        return formatDuration(lo) + ' – ' + formatDuration(hi);
    });
    var tempoRange = setupRange('tempo', document.getElementById('tempo-value'), function (lo, hi) {
        return lo + ' – ' + hi + ' BPM';
    });

    /* ── filtering ─────────────────────────────────────────────────────────── */

    function matches(row) {
        var data = row.dataset;

        if (sources.size && !sources.has(data.service)) return false;
        if (licences.size && !licences.has(data.licence)) return false;
        if (commercialOnly && data.commercial !== 'true') return false;

        // A missing length or tempo is not a reason to hide a track: most services
        // never report a BPM at all, and hiding those would gut the results.
        if (lengthRange && lengthRange.narrowed()) {
            var duration = Number(data.duration);
            if (duration > 0 && (duration < lengthRange.lo || duration > lengthRange.hi)) return false;
        }
        if (tempoRange && tempoRange.narrowed()) {
            var bpm = Number(data.bpm);
            if (bpm > 0 && (bpm < tempoRange.lo || bpm > tempoRange.hi)) return false;
        }

        return true;
    }

    function byPosition(a, b) {
        return Number(a.dataset.position) - Number(b.dataset.position);
    }

    function byDate(a, b) {
        if (a.dataset.date === b.dataset.date) return byPosition(a, b);
        return a.dataset.date < b.dataset.date ? 1 : -1;
    }

    function reorder() {
        ordered = rows.slice().sort(sort === 'newest' ? byDate : byPosition);

        var fragment = document.createDocumentFragment();
        ordered.forEach(function (row) {
            fragment.appendChild(row);
        });
        rowsList.appendChild(fragment);
    }

    var loadMore = document.getElementById('load-more');
    var noMatches = document.getElementById('no-matches');

    function apply() {
        var matched = 0;
        var services = new Set();

        ordered.forEach(function (row) {
            if (!matches(row)) {
                row.hidden = true;
                return;
            }
            matched++;
            services.add(row.dataset.service);
            row.hidden = matched > shown;
        });

        setAll('[data-visible-count]', String(matched));
        setAll('[data-result-noun]', plural(matched, 'result'));
        setAll('[data-visible-sources]', String(services.size));
        setAll('[data-source-noun]', plural(services.size, 'source'));
        if (loadMore) loadMore.hidden = matched <= shown;
        if (noMatches) noMatches.hidden = matched > 0;

        renderChips();
    }

    function refilter() {
        shown = PAGE_SIZE;
        apply();
    }

    /* ── controls ──────────────────────────────────────────────────────────── */

    Array.prototype.forEach.call(document.querySelectorAll('[data-facet]'), function (button) {
        button.addEventListener('click', function () {
            var key = button.dataset.facet;
            toggleIn(sources, key);
            button.setAttribute('aria-pressed', sources.has(key) ? 'true' : 'false');
            refilter();
        });
    });

    // Scoped to the chips: every result row also carries a data-licence, and binding
    // those would turn a click anywhere on a row into a filter toggle.
    Array.prototype.forEach.call(document.querySelectorAll('.licence-chip'), function (button) {
        button.addEventListener('click', function () {
            var key = button.dataset.licence;
            toggleIn(licences, key);
            button.setAttribute('aria-pressed', licences.has(key) ? 'true' : 'false');
            refilter();
        });
    });

    var commercialInput = document.getElementById('commercial-only');
    if (commercialInput) {
        commercialInput.addEventListener('change', function () {
            commercialOnly = commercialInput.checked;
            refilter();
        });
    }

    Array.prototype.forEach.call(document.querySelectorAll('[data-sort]'), function (button) {
        button.addEventListener('click', function () {
            if (sort === button.dataset.sort) return;
            sort = button.dataset.sort;

            Array.prototype.forEach.call(document.querySelectorAll('[data-sort]'), function (other) {
                other.setAttribute('aria-pressed', other === button ? 'true' : 'false');
            });
            reorder();
            refilter();
        });
    });

    if (loadMore) {
        loadMore.addEventListener('click', function () {
            shown += PAGE_SIZE;
            apply();
        });
    }

    Array.prototype.forEach.call(document.querySelectorAll('[data-reset]'), function (button) {
        button.addEventListener('click', reset);
    });

    function reset() {
        sources.clear();
        licences.clear();
        commercialOnly = false;

        Array.prototype.forEach.call(document.querySelectorAll('.facet, .licence-chip'), function (el) {
            el.setAttribute('aria-pressed', 'false');
        });
        if (commercialInput) commercialInput.checked = false;
        if (lengthRange) lengthRange.reset();
        if (tempoRange) tempoRange.reset();

        refilter();
    }

    function setAll(selector, text) {
        Array.prototype.forEach.call(document.querySelectorAll(selector), function (el) {
            el.textContent = text;
        });
    }

    function plural(count, noun) {
        return count === 1 ? noun : noun + 's';
    }

    function toggleIn(set, key) {
        if (set.has(key)) {
            set.delete(key);
        } else {
            set.add(key);
        }
    }

    /* ── two-handle range ──────────────────────────────────────────────────── */

    function setupRange(name, output, format) {
        var el = document.querySelector('[data-range="' + name + '"]');
        if (!el) return null;

        var lo = el.querySelector('[data-handle="lo"]');
        var hi = el.querySelector('[data-handle="hi"]');
        var fill = el.querySelector('.range-fill');
        var min = Number(lo.min);
        var max = Number(lo.max);

        var api = {
            lo: Number(lo.value),
            hi: Number(hi.value),
            narrowed: function () {
                return api.lo > min || api.hi < max;
            },
            reset: function () {
                lo.value = String(min);
                hi.value = String(max);
                sync();
            }
        };

        function sync() {
            api.lo = Number(lo.value);
            api.hi = Number(hi.value);

            var span = max - min || 1;
            fill.style.left = ((api.lo - min) / span * 100) + '%';
            fill.style.right = ((max - api.hi) / span * 100) + '%';
            if (output) output.textContent = format(api.lo, api.hi);
        }

        // The handles share a track, so each one stops at the other rather than crossing.
        lo.addEventListener('input', function () {
            if (Number(lo.value) > Number(hi.value)) lo.value = hi.value;
            sync();
            refilter();
        });
        hi.addEventListener('input', function () {
            if (Number(hi.value) < Number(lo.value)) hi.value = lo.value;
            sync();
            refilter();
        });

        sync();
        return api;
    }

    function formatDuration(seconds) {
        var total = Math.max(0, Math.round(seconds));
        var minutes = Math.floor(total / 60);
        var rest = total % 60;

        if (minutes >= 60) {
            return Math.floor(minutes / 60) + ':' + pad(minutes % 60) + ':' + pad(rest);
        }
        return minutes + ':' + pad(rest);
    }

    function pad(value) {
        return value < 10 ? '0' + value : String(value);
    }

    /* ── mobile: the rail is a bottom sheet ────────────────────────────────── */

    var rail = document.getElementById('filters');
    var openButton = document.getElementById('filters-open');
    var backdrop = null;

    if (rail && openButton) {
        backdrop = document.createElement('div');
        backdrop.className = 'sheet-backdrop';
        backdrop.hidden = true;
        document.body.appendChild(backdrop);

        openButton.addEventListener('click', function () {
            setSheet(rail.dataset.open !== 'true');
        });
        backdrop.addEventListener('click', function () {
            setSheet(false);
        });
        document.addEventListener('keydown', function (event) {
            if (event.key === 'Escape') setSheet(false);
        });
        Array.prototype.forEach.call(document.querySelectorAll('[data-close-sheet]'), function (button) {
            button.addEventListener('click', function () {
                setSheet(false);
            });
        });
    }

    function setSheet(open) {
        rail.dataset.open = open ? 'true' : 'false';
        openButton.setAttribute('aria-expanded', open ? 'true' : 'false');
        backdrop.hidden = !open;
    }

    /* ── the mobile chip row mirrors what the sheet is doing ───────────────── */

    var chiprow = document.getElementById('chiprow');
    var filtersCount = document.getElementById('filters-count');

    function renderChips() {
        if (!chiprow) return;

        var active = [];
        sources.forEach(function (key) {
            active.push(labelForFacet(key));
        });
        licences.forEach(function (key) {
            active.push(labelForLicence(key));
        });
        if (commercialOnly) active.push('Commercial OK');
        if (lengthRange && lengthRange.narrowed()) {
            active.push(formatDuration(lengthRange.lo) + '–' + formatDuration(lengthRange.hi));
        }
        if (tempoRange && tempoRange.narrowed()) {
            active.push(tempoRange.lo + '–' + tempoRange.hi + ' BPM');
        }

        if (filtersCount) filtersCount.textContent = active.length ? ' · ' + active.length : '';

        Array.prototype.forEach.call(chiprow.querySelectorAll('[data-chip]'), function (chip) {
            chip.remove();
        });
        active.forEach(function (label) {
            var chip = document.createElement('span');
            chip.className = 'tag tag-neutral';
            chip.setAttribute('data-chip', '');
            chip.textContent = label;
            chiprow.appendChild(chip);
        });
    }

    function labelForFacet(key) {
        var button = document.querySelector('[data-facet="' + key + '"] .facet-name');
        return button ? button.textContent : key;
    }

    function labelForLicence(key) {
        var button = document.querySelector('.licence-chip[data-licence="' + key + '"]');
        return button ? button.textContent : key;
    }

    apply();
})();

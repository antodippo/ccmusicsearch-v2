/*
 * Analytics reporting.
 *
 * Everything Google Analytics cannot work out on its own: what a search actually returned,
 * which result was clicked, and what the filters are being asked for. Page views, engagement
 * time and traffic sources need no help and are not repeated here.
 *
 * This is the only file that knows about gtag. The controls in search.js report through a
 * 'ccms:track' event instead, and it lives apart from search.js because that file returns
 * early when a search comes back empty — which is the case most worth measuring.
 */
(function () {
    'use strict';

    if (!window.ccmsAnalytics) {
        return;
    }

    function send(name, params) {
        gtag('event', name, params || {});
    }

    var app = document.querySelector('.app');
    var page = app ? app.dataset : {};
    var searchTerm = page.query || '';

    /* ── the search itself ─────────────────────────────────────────────────── */

    // GA4's own site search reports the term but not what came back, so a query that found
    // nothing looks like any other. Counting the results is the whole point of sending this
    // by hand; the built-in one is turned off in the property to avoid two rival datasets.
    if (page.hasQuery === 'true') {
        send('view_search_results', {
            search_term: searchTerm,
            result_count: Number(page.resultCount) || 0,
            source_count: Number(page.sourceCount) || 0
        });
    }

    /* ── clicking through to a service ─────────────────────────────────────── */

    // Outbound clicks are already tracked, but only down to the domain. The rank and the
    // licence are what say whether the ranking works and which licences people accept.
    document.addEventListener('click', function (event) {
        var link = event.target.closest ? event.target.closest('.row-listen') : null;
        if (!link) return;

        var row = link.closest('.row');
        if (!row) return;

        send('listen_click', {
            service: row.dataset.service,
            licence: row.dataset.licence,
            position: row.dataset.position,
            search_term: searchTerm
        });
    });

    /* ── the rest of the page ──────────────────────────────────────────────── */

    var themeToggle = document.getElementById('theme-toggle');
    if (themeToggle) {
        themeToggle.addEventListener('click', function () {
            send('theme_change', { theme: document.documentElement.getAttribute('data-theme') });
        });
    }

    document.addEventListener('ccms:track', function (event) {
        send(event.detail.name, event.detail.params);
    });
})();

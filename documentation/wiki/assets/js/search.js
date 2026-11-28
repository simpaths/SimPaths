/* Compact section results over Material's bundled Lunr worker and build index.
   The worker protocol is SETUP=0, READY=1, QUERY=2, RESULT=3 (Material 9.7).
   No second index, external service or search dependency is needed. */
(() => {
  const search = document.querySelector('[data-sp-search]');
  if (!search) return;
  const config = JSON.parse(document.getElementById('__config').textContent);
  const base = new URL(`${config.base}/`, document.baseURI);
  const workerURL = new URL(config.search, document.baseURI);
  const toggle = document.getElementById('__search');
  const input = search.querySelector('.md-search__input');
  const suggestions = search.querySelector('.sp-search-suggestions');
  const results = search.querySelector('.md-search-result');
  const list = results.querySelector('ol');
  const status = results.querySelector('[role="status"]');
  const more = results.querySelector('.sp-search-more');
  const scroll = search.querySelector('.md-search__scrollwrap');
  const output = search.querySelector('.md-search__output');
  const options = search.querySelector('.md-search__options');
  const share = search.querySelector('[data-sp-search-share]');
  const completion = search.querySelector('.md-search__suggest');
  const parser = document.createElement('template');
  const pages = new Map();
  const vocabulary = new Map();
  let worker, ready, timer, revision = 0, matches = [], shown = 0, suffix = '';
  let openedLocation = '', closingRequested = false;
  const pending = [];

  // Index excerpts can contain code, tables and links. Render text and our own
  // marks only, so a long code block cannot take over a result or nest links.
  const text = (html) => {
    parser.innerHTML = html.replace(/<\/(?:p|div|pre|li|tr|td|h[1-6])>/gi, '$& ');
    return parser.content.textContent.replace(/\s+/g, ' ').trim();
  };
  const mark = (element, value, terms) => {
    if (!terms.length) { element.textContent = value; return; }
    const escaped = terms.map(term => term.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'));
    const pattern = new RegExp(`(${escaped.join('|')})`, 'gi');
    value.split(pattern).forEach((part, i) => {
      const node = document.createTextNode(part);
      if (i % 2) {
        const highlight = document.createElement('mark');
        highlight.append(node);
        element.append(highlight);
      } else element.append(node);
    });
  };
  const request = (query) => new Promise((resolve, reject) => {
    pending.push({ resolve, reject });
    worker.postMessage({ type: 2, data: query });
  });
  const initialize = () => {
    if (ready) return ready;
    ready = (async () => {
      const response = await fetch(new URL('search/search_index.json', base));
      if (!response.ok) throw new Error('Search index unavailable');
      const index = await response.json();
      for (const doc of index.docs) {
        if (!doc.location.includes('#')) pages.set(doc.location, text(doc.title));
        // Restrict corrections to ordinary words; identifiers and query syntax
        // remain exact. Frequency breaks ties in favour of common site terms.
        for (const word of text(`${doc.title} ${doc.text}`).toLowerCase().match(/\b[a-z]{5,24}\b/g) || []) {
          vocabulary.set(word, (vocabulary.get(word) || 0) + 1);
        }
      }
      await new Promise((resolve, reject) => {
        worker = new Worker(workerURL);
        const timeout = setTimeout(() => { worker.terminate(); reject(new Error('Search initialization timed out')); }, 10000);
        worker.addEventListener('error', () => {
          clearTimeout(timeout);
          reject(new Error('Search worker unavailable'));
          pending.splice(0).forEach(item => item.reject(new Error('Search worker unavailable')));
          worker.terminate();
          ready = null;
        });
        worker.addEventListener('message', ({ data }) => {
          if (data.type === 1) { clearTimeout(timeout); resolve(); }
          if (data.type === 3) pending.shift()?.resolve(data.data);
        });
        worker.postMessage({ type: 0, data: { ...index, options: { suggest: true } } });
      });
    })().catch(error => { ready = null; throw error; });
    return ready;
  };

  const correctedQuery = (query) => {
    if (!/^[a-zA-Z\s]+$/.test(query)) return query;
    return query.split(/(\s+)/).map(token => {
      if (!/^[A-Z]?[a-z]{4,23}$/.test(token)) return token;
      const word = token.toLowerCase();
      if (vocabulary.has(word)) return token;
      // Prefixes are intentional while typing, not spelling mistakes.
      if ([...vocabulary.keys()].some(candidate => candidate.startsWith(word))) return token;
      const candidates = new Set();
      const add = candidate => { if (vocabulary.has(candidate)) candidates.add(candidate); };
      for (let i = 0; i <= word.length; i++) {
        const left = word.slice(0, i), right = word.slice(i);
        if (right) add(left + right.slice(1));
        if (right.length > 1) add(left + right[1] + right[0] + right.slice(2));
        for (const letter of 'abcdefghijklmnopqrstuvwxyz') {
          add(left + letter + right);
          if (right) add(left + letter + right.slice(1));
        }
      }
      const ranked = [...candidates].sort((a, b) => vocabulary.get(b) - vocabulary.get(a));
      // Leave ambiguous corrections alone instead of guessing aggressively.
      return ranked.length === 1 || (ranked.length > 1 && vocabulary.get(ranked[0]) >= 3 * vocabulary.get(ranked[1]))
        ? ranked[0] : token;
    }).join('');
  };

  const flatten = data => {
    const unique = new Map();
    for (const item of data.items.flat()) {
      if (item.score <= 0) continue; // Worker supplies unmatched parent records for grouping.
      const key = item.location;
      if (!unique.has(key) || unique.get(key).score < item.score) unique.set(key, item);
    }
    return [...unique.values()].sort((a, b) =>
      Number(Object.values(b.terms).every(Boolean)) - Number(Object.values(a.terms).every(Boolean)) || b.score - a.score);
  };
  const context = location => {
    const path = location.split('#')[0];
    const parts = path.split('/').filter(Boolean);
    const titles = parts.map((_, i) => pages.get(`${parts.slice(0, i + 1).join('/')}/`)).filter(Boolean);
    if (!location.includes('#')) titles.pop();
    return [...new Set(titles)].join(' / ') || 'SimPaths';
  };
  const appendResults = () => {
    const fragment = document.createDocumentFragment();
    matches.slice(shown, shown + 20).forEach(item => {
      const li = document.createElement('li');
      li.className = 'md-search-result__item';
      const link = document.createElement('a');
      link.className = 'md-search-result__link';
      const url = new URL(item.location, base);
      const terms = Object.entries(item.terms).filter(([, found]) => found).map(([term]) => term);
      url.searchParams.set('h', terms.join(' '));
      link.href = url.href;
      const path = document.createElement('span');
      path.className = 'sp-search-path';
      path.textContent = context(item.location);
      const title = document.createElement('h3');
      title.className = 'sp-search-title';
      const snippet = document.createElement('p');
      snippet.className = 'sp-search-excerpt';
      parser.innerHTML = `${item.title} ${item.text}`;
      const highlights = [...new Set([...parser.content.querySelectorAll('mark')].map(node => node.textContent))]
        .filter(Boolean).sort((a, b) => b.length - a.length);
      mark(title, text(item.title), highlights);
      const plain = text(item.text);
      const first = highlights.map(term => plain.toLowerCase().indexOf(term.toLowerCase())).filter(i => i >= 0);
      let start = Math.max(0, (first.length ? Math.min(...first) : 0) - 60);
      if (start) start = plain.lastIndexOf(' ', start) + 1;
      const end = Math.min(plain.length, start + 240);
      const excerpt = plain.slice(start, end).replace(/\s+\S*$/, end < plain.length ? '' : '$&');
      mark(snippet, `${start ? '… ' : ''}${excerpt}${end < plain.length ? '…' : ''}`, highlights);
      link.append(path, title, snippet);
      li.append(link);
      fragment.append(li);
    });
    list.append(fragment);
    shown = Math.min(shown + 20, matches.length);
    more.hidden = shown >= matches.length;
  };

  const run = async (query, ticket) => {
    try {
      await initialize();
      if (ticket !== revision) return;
      const exact = await request(query);
      if (ticket !== revision) return;
      let data = exact, correction = '';
      // Prefer exact/prefix results. Only recover spelling if no section matches
      // all the entered words, and tell the reader which correction was used.
      if (!flatten(exact).some(item => Object.values(item.terms).every(Boolean))) {
        const corrected = correctedQuery(query);
        if (corrected !== query) {
          const fallback = await request(corrected);
          if (ticket !== revision) return;
          if (fallback.items.length) { data = fallback; correction = corrected; }
        }
      }
      matches = flatten(data);
      shown = 0;
      list.replaceChildren();
      appendResults();
      results.removeAttribute('aria-busy');
      status.textContent = matches.length
        ? `${matches.length} matching ${matches.length === 1 ? 'section' : 'sections'}${correction ? ` · Showing results for “${correction}”` : ''}`
        : 'No matching results. Try a different term or fewer words.';
      const last = query.match(/[a-z]+$/i)?.[0] || '';
      const suggestion = !correction && last.length > 1 && data.suggest?.find(word => word.startsWith(last.toLowerCase()) && word.length > last.length);
      suffix = suggestion ? suggestion.slice(last.length) : '';
      completion.textContent = suffix ? input.value + suffix : '';
    } catch {
      if (ticket !== revision) return;
      results.removeAttribute('aria-busy');
      status.textContent = 'Search is unavailable. Please reload and try again.';
    }
  };
  const update = () => {
    clearTimeout(timer);
    const ticket = ++revision, query = input.value.trim();
    search.classList.toggle('sp-search-has-query', Boolean(query));
    options.inert = !query;
    suggestions.hidden = !toggle.checked || Boolean(query);
    results.hidden = !toggle.checked || !query;
    list.replaceChildren();
    matches = [];
    more.hidden = true;
    suffix = '';
    completion.textContent = '';
    scroll.scrollTop = 0;
    const url = new URL(location.href);
    url.searchParams.delete('h');
    url.searchParams.set('q', query);
    url.hash = '';
    share.href = url.href;
    if (!query || !toggle.checked) { status.textContent = ''; results.removeAttribute('aria-busy'); return; }
    results.setAttribute('aria-busy', 'true');
    status.textContent = 'Searching…';
    timer = setTimeout(() => run(query, ticket), 100);
  };
  suggestions.querySelectorAll('a[href]').forEach(link => { link.href = link.href; });
  const syncOpen = (event) => {
    // Material closes overlays shortly after navigation. Ignore that delayed
    // close if the reader has already reopened search on the destination page.
    if (event && !toggle.checked && !closingRequested && openedLocation === location.href && search.contains(document.activeElement)) {
      // A checkbox ignores a nested click while its previous click is firing.
      toggle.checked = true;
      toggle.dispatchEvent(new Event('change', { bubbles: true }));
      return;
    }
    output.inert = !toggle.checked;
    input.tabIndex = toggle.checked ? 0 : -1;
    search.querySelectorAll('.md-search__options > *').forEach(control => { control.tabIndex = toggle.checked ? 0 : -1; });
    if (toggle.checked) {
      openedLocation = location.href;
      closingRequested = false;
      search.setAttribute('aria-modal', 'true');
      initialize().catch(() => {}); // Warm the worker while the reader chooses a suggested page.
    } else {
      search.removeAttribute('aria-modal');
      const url = new URL(location.href);
      if (event && url.searchParams.has('q')) { url.searchParams.delete('q'); history.replaceState(history.state, '', url); }
    }
    update();
  };
  toggle.addEventListener('change', syncOpen);
  search.addEventListener('sp-search-close', () => { closingRequested = true; });
  input.addEventListener('input', event => { if (!event.isComposing) update(); });
  input.addEventListener('compositionend', update);
  input.form.addEventListener('reset', event => {
    event.preventDefault();
    input.value = '';
    update();
    input.focus();
  });
  input.form.addEventListener('submit', event => { event.preventDefault(); list.querySelector('a')?.click(); });
  more.addEventListener('click', () => {
    const next = shown;
    appendResults();
    list.children[next]?.querySelector('a').focus();
  });
  share.addEventListener('click', async event => {
    if (!navigator.clipboard) return; // The href remains a usable search permalink.
    event.preventDefault();
    try {
      await navigator.clipboard.writeText(share.href);
      status.textContent = 'Search link copied.';
    } catch { status.textContent = 'Unable to copy. Use the Share search link address.'; }
  });
  search.addEventListener('click', event => {
    if (event.target.closest('.md-search__overlay')) closingRequested = true;
    if (event.target.closest('.md-search-result__link, .sp-search-suggestions__link')) {
      closingRequested = true;
      if (toggle.checked) toggle.click();
    }
  });
  document.addEventListener('keydown', event => {
    if (event.isComposing || event.altKey) return;
    const editing = event.target.closest('input, textarea, select, [contenteditable="true"]');
    if ((event.key === '/' && !editing && !event.ctrlKey && !event.metaKey) ||
        (event.key.toLowerCase() === 'k' && (event.ctrlKey || event.metaKey))) {
      event.preventDefault();
      if (!toggle.checked) toggle.click();
      input.focus(); input.select();
      return;
    }
    if (!toggle.checked || event.ctrlKey || event.metaKey) return;
    const links = [...(input.value.trim() ? list : suggestions).querySelectorAll('a[href]')];
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      if (event.key === 'ArrowDown' && document.activeElement === links.at(-1) && !more.hidden) appendResults();
      const targets = [input, ...(input.value.trim() ? list : suggestions).querySelectorAll('a[href]')];
      const index = Math.max(0, targets.indexOf(document.activeElement));
      targets[(index + targets.length + (event.key === 'ArrowUp' ? -1 : 1)) % targets.length].focus();
    } else if (event.key === 'Tab') {
      const targets = [...search.querySelectorAll('input, button, a[href]')]
        .filter(node => !node.closest('[inert]') && node.getClientRects().length && getComputedStyle(node).visibility === 'visible');
      const index = targets.indexOf(document.activeElement);
      event.preventDefault();
      targets[(index + targets.length + (event.shiftKey ? -1 : 1)) % targets.length]?.focus();
    } else if (event.key === 'ArrowRight' && event.target === input && suffix && input.selectionStart === input.value.length) {
      input.value += suffix; update();
    } else if (event.key === 'Enter' && event.target === input) {
      event.preventDefault();
      if (input.value.trim()) links[0]?.click();
    } else if (event.key.length === 1 && event.target !== input && event.key !== ' ') {
      input.focus();
    }
  });
  const readQuery = () => {
    const query = new URL(location.href).searchParams.get('q');
    if (query !== null) {
      input.value = query;
      if (!toggle.checked) toggle.click();
      update(); input.focus();
    }
  };
  syncOpen();
  if (typeof document$ !== 'undefined') document$.subscribe(readQuery);
  else readQuery();
})();

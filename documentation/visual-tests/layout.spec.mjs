import { expect, test } from "@playwright/test";


test("validation uses consistent body typography and correctly nested instructions", async ({ page }) => {
  await page.goto("/validation/");
  const article = page.locator(".md-content__inner");
  await expect(article.locator(".page-intro, strong, em")).toHaveCount(0);
  const typography = await article.locator("p, li, code").evaluateAll(elements =>
    elements.filter(element => element.textContent.trim()).map(element => {
      const style = getComputedStyle(element);
      return [style.fontFamily, style.fontSize, style.fontWeight, style.color].join("|");
    })
  );
  expect(new Set(typography).size).toBe(1);
  const lists = article.locator(":scope > ol");
  await expect(lists).toHaveCount(4);
  for (const [index, count] of [2, 3, 4, 4].entries()) {
    await expect(lists.nth(index).locator(":scope > li")).toHaveCount(count);
  }
  await expect(lists.nth(1).locator(":scope > li > ul")).toHaveCount(2);
  await expect(article).not.toContainText("To be completed");
  await expect(article).not.toContainText("ablility");
  const headings = await article.locator("h1, h2, h3").evaluateAll(elements => Object.fromEntries(
    elements.map(element => [element.tagName, parseFloat(getComputedStyle(element).fontSize)])
  ));
  expect(headings.H1).toBeGreaterThan(headings.H2);
  expect(headings.H2).toBeGreaterThan(headings.H3);
});


test("inline code is readable and uses restrained syntax colours", async ({ page }) => {
  await page.goto("/developer-guide/repository-guide/");
  const method = page.locator("li > code").filter({ hasText: /^SimPathsModel\.buildSchedule\(\)$/ }).first();
  await expect(method).toHaveText("SimPathsModel.buildSchedule()");
  await expect(method.locator(".sp-code-type")).toHaveText("SimPathsModel");
  await expect(method.locator(".sp-code-function")).toHaveText("buildSchedule");
  const inline = await method.evaluate(element => ({
    size: parseFloat(getComputedStyle(element).fontSize),
    surroundingSize: parseFloat(getComputedStyle(element.parentElement).fontSize),
    weight: Number(getComputedStyle(element).fontWeight),
    background: getComputedStyle(element).backgroundColor
  }));
  expect(inline.size / inline.surroundingSize).toBeCloseTo(0.95, 2);
  expect(inline.weight).toBeLessThanOrEqual(500);
  expect(inline.background).toBe("rgba(0, 0, 0, 0)");

  for (const scheme of ["default", "slate"]) {
    await page.locator("body").evaluate((body, value) => body.setAttribute("data-md-color-scheme", value), scheme);
    const colours = await method.evaluate(element => [element, ...element.querySelectorAll("span")].map(
      node => getComputedStyle(node).color
    ));
    expect(new Set(colours).size).toBe(3);
  }
  await page.locator("body").evaluate(body => body.setAttribute("data-md-color-scheme", "default"));

  const java = page.locator(".language-java").filter({ hasText: "getProbabilities" });
  await expect(java.locator(".nc").filter({ hasText: /^ManagerRegressions$/ })).toHaveCount(1);
  await expect(java.locator(".nf").filter({ hasText: /^getProbabilities$/ })).toHaveCount(1);
  await expect(java.locator("code")).toHaveText(/ManagerRegressions\.getProbabilities\(this, RegressionName\.HealthH1\);/);
  await expect(java.locator(".md-clipboard, .md-code__button").first()).toBeVisible();

  await page.goto("/user-guide/single-runs/");
  const option = page.locator("p > code").filter({ hasText: /^--rewrite-policy-schedule$/ }).first();
  await expect(option.locator("span")).toHaveCount(0);
  await expect(option).toHaveCSS("color", "rgb(36, 42, 49)");
});

const routes = [
  ["home", "/"],
  ["model", "/overview/"],
  ["documentation", "/documentation/"],
  ["roadmap", "/overview/roadmap/"],
  ["research", "/research/"],
  ["funding", "/funding/"],
  ["single-runs", "/user-guide/single-runs/"],
  ["uncertainty-analysis", "/user-guide/uncertainty-analysis/"],
  ["repository-guide", "/developer-guide/repository-guide/"],
  ["developer-guide", "/developer-guide/"],
  ["new-variable", "/developer-guide/how-to/new-variable/"],
  ["querying-database", "/jasmine-reference/querying-database/"],
  ["saving-outputs", "/jasmine-reference/saving-outputs/"],
  ["matching-library", "/jasmine-reference/matching-library/"],
  ["regression-library", "/jasmine-reference/regression-library/"],
  ["module-ageing", "/overview/modules/ageing/"],
  ["module-education", "/overview/modules/education/"],
  ["module-health", "/overview/modules/health/"],
  ["module-family-composition", "/overview/modules/family-composition/"],
  ["module-social-care", "/overview/modules/social-care/"],
  ["module-investment-income", "/overview/modules/investment-income/"],
  ["module-labour-income", "/overview/modules/labour-income/"],
  ["module-disposable-income", "/overview/modules/disposable-income/"],
  ["module-consumption", "/overview/modules/consumption/"],
  ["module-mental-health", "/overview/modules/mental-health/"],
  ["module-statistical-display", "/overview/modules/statistical-display/"]
];

for (const [name, path] of routes) {
  test(`${name} keeps its layout at the current viewport`, async ({ page }, testInfo) => {
    await page.goto(path, { waitUntil: "domcontentloaded" });
    await page.evaluate(() => document.fonts.ready);

    await expect(page.locator("main")).toBeVisible();
    await expect(page.locator("h1").first()).toBeVisible();

    const titlePresentation = await page.locator("h1").first().evaluate((element) => ({
      borderBottomWidth: getComputedStyle(element).borderBottomWidth,
      markerContent: getComputedStyle(element, "::after").content
    }));
    expect(titlePresentation).toEqual({
      borderBottomWidth: "0px",
      markerContent: "none"
    });

    const layout = await page.evaluate(() => ({
      horizontalOverflow: document.documentElement.scrollWidth - window.innerWidth,
      dimensions: {
        innerWidth: window.innerWidth,
        documentClientWidth: document.documentElement.clientWidth,
        documentScrollWidth: document.documentElement.scrollWidth,
        bodyClientWidth: document.body.clientWidth,
        bodyScrollWidth: document.body.scrollWidth,
        bodyRect: Math.round(document.body.getBoundingClientRect().width)
      },
      overflowingElements: [...document.querySelectorAll("body *")]
        .map((element) => {
          const rect = element.getBoundingClientRect();
          return {
            tag: element.tagName.toLowerCase(),
            classes: element.className?.toString().slice(0, 120),
            left: Math.round(rect.left),
            right: Math.round(rect.right),
            width: Math.round(rect.width)
          };
        })
        .filter(({ left, right }) => left < -1 || right > window.innerWidth + 1)
        .slice(0, 12),
      loadedStyles: [...document.styleSheets]
        .map((sheet) => sheet.href)
        .filter((href) => href && href.includes("/assets/css/"))
        .map((href) => new URL(href).pathname.split("/").pop())
    }));

    expect(
      layout.horizontalOverflow,
      `Dimensions: ${JSON.stringify(layout.dimensions)}; overflowing elements: ${JSON.stringify(layout.overflowingElements)}`
    ).toBeLessThanOrEqual(8);
    expect(layout.loadedStyles).toEqual([
      "01-foundation.css",
      "02-shell-navigation.css",
      "03-content.css",
      "04-landing-components.css",
      "05-site-chrome.css",
      "06-page-sections.css",
      "07-roadmap.css",
      "08-home.css"
    ]);

    if (name === "home") {
      await expect(page.getByRole("link", { name: "All research" })).toBeVisible();
    }

    await page.screenshot({
      path: testInfo.outputPath(`${name}.png`),
      fullPage: true,
      animations: "disabled"
    });
  });
}

test("audited guides expose the checked specifications and usable examples", async ({ page }) => {
  for (const [route, title] of [
    ["/documentation/", "Documentation"],
    ["/research/", "Research"],
    ["/funding/", "Funding"]
  ]) {
    await page.goto(route);
    await expect(page).toHaveTitle(new RegExp(title + ".*SimPaths"));
    await expect(page.locator("h1")).toHaveCount(1);
  }

  await page.goto("/overview/modules/health/");
  await expect(page.locator("article")).toContainText("generalised ordered logit");
  await expect(page.locator("article")).toContainText("distinct from the continuous SF-12");

  await page.goto("/overview/modules/education/");
  await expect(page.locator("article")).toContainText("generalised ordered logit model (E2)");

  await page.goto("/user-guide/single-runs/");
  await expect(page.locator("article")).toContainText("They cannot be used together");
  await expect(page.locator("article pre")).not.toHaveCount(0);

  await page.goto("/user-guide/uncertainty-analysis/");
  await expect(page.locator("article")).toContainText("static final boolean");
  await expect(page.locator("article")).toContainText("not a supported run-time YAML");

  await page.goto("/jasmine-reference/querying-database/");
  await expect(page.locator("article table tbody tr")).toHaveCount(3);
  await expect(page.locator("article")).not.toContainText("erDiagram");

  await page.goto("/overview/how-to-cite/");
  await expect(page.locator("article a[href*='research']")).toHaveCount(1);
  await expect(page.locator("article")).not.toContainText("CeMPA WP");

  await page.goto("/overview/simulated-modules/");
  await expect(page.locator("article h2")).toHaveCount(11);
  await expect(page.locator("article")).not.toContainText("ordered probit");
});

test("model overview keeps the established reading measure", async ({ page }) => {
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/overview/", { waitUntil: "domcontentloaded" });

  await expect(page.locator("body")).toHaveClass(/sp-reserve-toc-space/);

  const overviewMeasure = await page.locator(".model-overview").evaluate((element) => {
    return {
      width: element.getBoundingClientRect().width,
      overflow: document.documentElement.scrollWidth - window.innerWidth
    };
  });

  await page.goto("/overview/model-description/", { waitUntil: "domcontentloaded" });
  const standardMeasure = await page.locator(".md-content__inner").evaluate((element) =>
    element.getBoundingClientRect().width
  );

  expect(Math.abs(overviewMeasure.width - standardMeasure)).toBeLessThanOrEqual(1);
  expect(overviewMeasure.overflow).toBeLessThanOrEqual(8);
});

test("validation omits its redundant desktop navigation", async ({ page }) => {
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/validation/", { waitUntil: "domcontentloaded" });

  await expect(page.locator("body")).toHaveClass(/sp-page-validation/);

  const desktop = await page.evaluate(() => ({
    primaryDisplay: getComputedStyle(document.querySelector(".md-sidebar--primary")).display,
    secondaryDisplay: getComputedStyle(document.querySelector(".md-sidebar--secondary")).display,
    contentWidth: document.querySelector(".md-content__inner").getBoundingClientRect().width
  }));

  expect(desktop.primaryDisplay).toBe("none");
  expect(desktop.secondaryDisplay).not.toBe("none");
  expect(desktop.contentWidth).toBeGreaterThanOrEqual(800);
  expect(desktop.contentWidth).toBeLessThanOrEqual(841);

  await page.setViewportSize({ width: 390, height: 844 });
  const mobilePrimaryVisibility = await page
    .locator(".md-sidebar--primary")
    .evaluate((element) => getComputedStyle(element).visibility);
  expect(mobilePrimaryVisibility).toBe("visible");
});

test("roadmap contains public priorities rather than editorial notes", async ({ page }) => {
  await page.goto("/overview/roadmap/", { waitUntil: "domcontentloaded" });

  await expect(page.getByRole("heading", { name: "Working on now", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Planned work", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Capabilities in the pipeline", exact: true })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Model foundations", exact: true })).toHaveCount(0);
  await expect(
    page.locator(".roadmap-stage--now").getByRole("heading", {
      name: "Wealth across the life course",
      exact: true
    })
  ).toBeVisible();
  await expect(page.locator(".roadmap-stage")).toHaveCount(3);
  await expect(page.locator(".roadmap-item")).toHaveCount(13);
  await expect(page.locator(".roadmap-impact")).toHaveCount(0);
  await expect(page.locator(".roadmap-page > ol")).toHaveCount(0);
  await expect(page.locator('.roadmap-meta a[href*="/issues/"]')).toHaveCount(25);

  const content = await page.locator(".roadmap-page").innerText();
  for (const editorialNote of [
    "Public issue state",
    "Proposed update cycle",
    "reconfirmed before publication",
    "How this roadmap is maintained",
    "internal development project",
    "Recent progress"
  ]) {
    expect(content).not.toContain(editorialNote);
  }

  for (const completedIssue of [404, 428, 432, 436]) {
    await expect(page.locator(`a[href$="/issues/${completedIssue}"]`)).toHaveCount(0);
  }

  const presentation = await page.evaluate(() => ({
    headingRules: [...document.querySelectorAll(".roadmap-horizon h2, .roadmap-item h3")].map(
      (heading) => getComputedStyle(heading).borderTopWidth
    ),
    sectionHeadingMaxWidths: [...document.querySelectorAll(".roadmap-stage__heading")].map(
      (heading) => getComputedStyle(heading).maxWidth
    ),
    sectionSummaryMaxWidths: [...document.querySelectorAll(".roadmap-stage__heading > p")].map(
      (paragraph) => getComputedStyle(paragraph).maxWidth
    ),
    itemAlignment: [...document.querySelectorAll(".roadmap-item > p:not(.roadmap-meta)")].map(
      (paragraph) => getComputedStyle(paragraph).textAlign
    ),
    overflow: document.documentElement.scrollWidth - window.innerWidth
  }));

  expect(presentation.headingRules.every((width) => width === "0px")).toBe(true);
  expect(presentation.sectionHeadingMaxWidths.every((width) => width === "none")).toBe(true);
  expect(presentation.sectionSummaryMaxWidths.every((width) => width === "none")).toBe(true);
  expect(presentation.itemAlignment.every((alignment) => alignment === "left")).toBe(true);
  expect(presentation.overflow).toBeLessThanOrEqual(8);
});

test("site state exposes explicit styling hooks", async ({ page }) => {
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/documentation/", { waitUntil: "domcontentloaded" });

  await expect(page.locator("body")).toHaveClass(/sp-page-docs-index/);
  await expect(page.locator("body")).toHaveClass(/sp-tab-documentation/);
  await expect(page.locator(".sp-nav-container-active")).not.toHaveCount(0);

  const searchTrigger = page.locator(".md-search__form > label.md-search__icon");
  const closedSearch = await page.locator(".md-search").boundingBox();
  expect(closedSearch.width).toBeGreaterThanOrEqual(145);
  expect(closedSearch.width).toBeLessThanOrEqual(165);

  await searchTrigger.click();
  await expect(page.locator("body")).toHaveClass(/sp-search-open/);
  await expect(page.locator(".md-search__input")).toHaveAttribute("placeholder", "Search SimPaths");
  await expect.poll(async () => (await page.locator(".md-search").boundingBox()).width).toBeGreaterThan(740);

  const openSearch = await page.evaluate(() => {
    const rect = (selector) => document.querySelector(selector).getBoundingClientRect();
    const search = rect(".md-search");
    const inner = rect(".md-search__inner");
    const output = rect(".md-search__output");
    const form = rect(".md-search__form");
    const arrow = rect(".md-search__form > label.md-search__icon svg:last-child");
    const title = getComputedStyle(document.querySelector(".md-header__title"));
    const overlay = getComputedStyle(document.querySelector(".md-search__overlay"));
    return {
      searchWidth: search.width,
      innerWidth: inner.width,
      rightEdgeDifference: Math.abs(search.right - output.right),
      arrowVerticalOffset: Math.abs((form.top + form.bottom - arrow.top - arrow.bottom) / 2),
      titleOpacity: title.opacity,
      overlayBackground: overlay.backgroundColor
    };
  });

  expect(openSearch.searchWidth).toBeGreaterThan(740);
  expect(openSearch.searchWidth).toBeLessThanOrEqual(760);
  expect(Math.abs(openSearch.searchWidth - openSearch.innerWidth)).toBeLessThanOrEqual(1);
  expect(openSearch.rightEdgeDifference).toBeLessThanOrEqual(1);
  expect(openSearch.arrowVerticalOffset).toBeLessThanOrEqual(1);
  expect(openSearch.titleOpacity).toBe("0");
  expect(openSearch.overlayBackground).toBe("rgba(0, 0, 0, 0)");

  const searchInput = page.locator(".md-search__input");
  await searchInput.fill("regression");
  await expect(searchInput).toHaveValue("regression");
  await searchInput.fill("");

  await searchTrigger.click();
  await expect(page.locator("body")).not.toHaveClass(/sp-search-open/);
  await expect.poll(async () => (await page.locator(".md-search").boundingBox()).width).toBeLessThanOrEqual(165);
  const restoredSearch = await page.locator(".md-search").boundingBox();
  expect(Math.abs(restoredSearch.width - closedSearch.width)).toBeLessThanOrEqual(1);

  await page.goto("/funding/", { waitUntil: "domcontentloaded" });
  await expect(page.locator("body")).toHaveClass(/sp-page-funding/);

  await page.goto("/overview/modules/ageing/", { waitUntil: "domcontentloaded" });
  await expect(page.locator("body")).toHaveClass(/sp-tab-model/);
  await expect(page.locator(".sp-modules-branch")).not.toHaveCount(0);
});

test("homepage provides useful task routes and an editorial research band", async ({ page }) => {
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/", { waitUntil: "domcontentloaded" });

  await expect(page.locator(".simpaths-home-explore")).toHaveCount(0);
  await expect(page.getByRole("heading", { name: "Use SimPaths" })).toBeVisible();
  await expect(page.locator(".simpaths-home-paths__header p")).toHaveCount(0);
  await expect(page.locator(".simpaths-home-paths__route")).toHaveCount(4);
  await expect(page.locator(".simpaths-home-paths__links a")).toHaveCount(12);
  await expect(page.locator(".simpaths-home-intro-band__access")).toHaveCount(2);
  await expect(page.getByRole("heading", { name: "How to cite" })).toBeVisible();
  await expect(page.locator(".simpaths-home-citation-band__guidance a")).toHaveCount(2);

  const presentation = await page.evaluate(() => {
    const paths = document.querySelector(".simpaths-home-paths");
    const routes = document.querySelector(".simpaths-home-paths__routes");
    const routeItems = [...document.querySelectorAll(".simpaths-home-paths__route")];
    const band = document.querySelector(".simpaths-home-research-band");
    const citation = document.querySelector(".simpaths-home-citation-band");
    const header = document.querySelector(".simpaths-home-research-band .research-header");
    const list = document.querySelector(".simpaths-home-research-band .research-list");
    const entries = [...document.querySelectorAll(".simpaths-home-research-band .research-entry")];
    const titles = [...document.querySelectorAll(".simpaths-home-research-band .research-title")];
    const labels = [...document.querySelectorAll(".simpaths-home-research-band .research-label")];
    const journals = [...document.querySelectorAll(".simpaths-home-research-band .research-journal")];
    const authors = [...document.querySelectorAll(".simpaths-home-research-band .research-authors")];

    return {
      pathHeight: paths.getBoundingClientRect().height,
      pathColumns: getComputedStyle(routes).gridTemplateColumns.split(" ").length,
      pathBorders: routeItems.map((item) => getComputedStyle(item).borderTopWidth),
      pathFrameBackground: getComputedStyle(routes).backgroundColor,
      pathRouteBackgrounds: routeItems.map((item) => getComputedStyle(item).backgroundColor),
      pathRouteHeights: routeItems.map((item) => Math.round(item.getBoundingClientRect().height)),
      pathDescriptionCount: document.querySelectorAll(".simpaths-home-paths__route > p").length,
      pathBeforeResearch: paths.getBoundingClientRect().bottom <= band.getBoundingClientRect().top,
      citationAfterResearch: band.getBoundingClientRect().bottom <= citation.getBoundingClientRect().top,
      citationColumns: getComputedStyle(citation.querySelector(".simpaths-home-citation-band__inner")).gridTemplateColumns.split(" ").length,
      citationOverflow: citation.scrollWidth - citation.clientWidth,
      bandHeight: band.getBoundingClientRect().height,
      columns: getComputedStyle(list).gridTemplateColumns.split(" ").length,
      entryCount: entries.length,
      entryHeights: entries.map((entry) => Math.round(entry.getBoundingClientRect().height)),
      entryBorders: entries.map((entry) => getComputedStyle(entry).borderTopWidth),
      entryBackgrounds: entries.map((entry) => getComputedStyle(entry).backgroundColor),
      entryCursors: entries.map((entry) => getComputedStyle(entry).cursor),
      entryHrefs: entries.map((entry) => entry.href),
      entryArrows: entries.map((entry) => getComputedStyle(entry, "::after").content),
      titleWeights: titles.map((title) => Number(getComputedStyle(title).fontWeight)),
      labelColors: labels.map((label) => getComputedStyle(label).color),
      journalColors: journals.map((journal) => getComputedStyle(journal).color),
      authorColors: authors.map((author) => getComputedStyle(author).color),
      summaryCount: document.querySelectorAll(".simpaths-home-research-band .research-summary").length,
      headerLeft: Math.round(header.getBoundingClientRect().left),
      firstEntryLeft: Math.round(entries[0].getBoundingClientRect().left)
    };
  });

  expect(presentation.pathHeight).toBeGreaterThan(420);
  expect(presentation.pathHeight).toBeLessThan(550);
  expect(presentation.pathColumns).toBe(4);
  expect(presentation.pathBorders).toEqual(["0px", "0px", "0px", "0px"]);
  expect(presentation.pathFrameBackground).toBe("rgb(222, 218, 208)");
  expect(new Set(presentation.pathRouteBackgrounds)).toEqual(new Set(["rgb(255, 254, 250)"]));
  expect(new Set(presentation.pathRouteHeights).size).toBe(1);
  expect(presentation.pathDescriptionCount).toBe(0);
  expect(presentation.pathBeforeResearch).toBe(true);
  expect(presentation.citationAfterResearch).toBe(true);
  expect(presentation.citationColumns).toBe(2);
  expect(presentation.citationOverflow).toBe(0);
  expect(presentation.bandHeight).toBeGreaterThan(500);
  expect(presentation.columns).toBe(3);
  expect(presentation.entryCount).toBe(3);
  expect(new Set(presentation.entryHeights).size).toBe(1);
  expect(presentation.entryBorders).toEqual(["0px", "0px", "0px"]);
  expect(presentation.entryBackgrounds).toEqual([
    "rgb(255, 255, 255)",
    "rgb(255, 255, 255)",
    "rgb(255, 255, 255)"
  ]);
  expect(presentation.entryCursors).toEqual(["pointer", "pointer", "pointer"]);
  expect(presentation.entryHrefs.every((href) => href.startsWith("https://"))).toBe(true);
  expect(presentation.entryArrows.every((arrow) => arrow !== "none")).toBe(true);
  expect(presentation.titleWeights.every((weight) => weight < 600)).toBe(true);
  expect(new Set(presentation.labelColors).size).toBe(3);
  expect(new Set(presentation.journalColors)).toEqual(new Set(["rgb(82, 97, 113)"]));
  expect(new Set(presentation.authorColors)).toEqual(new Set(["rgb(102, 113, 125)"]));
  expect(presentation.summaryCount).toBe(0);
  expect(presentation.headerLeft).toBeLessThan(presentation.firstEntryLeft);

  const firstResearchEntry = page.locator(".simpaths-home-research-band .research-entry").first();
  await firstResearchEntry.hover();
  await page.waitForTimeout(250);
  const hoverState = await firstResearchEntry.evaluate((entry) => ({
    background: getComputedStyle(entry).backgroundColor,
    arrowBackground: getComputedStyle(entry, "::after").backgroundColor,
    titleDecoration: getComputedStyle(entry.querySelector(".research-title")).textDecorationLine
  }));
  expect(hoverState.background).not.toBe("rgb(255, 255, 255)");
  expect(hoverState.arrowBackground).toBe("rgb(255, 255, 255)");
  expect(hoverState.titleDecoration).toContain("underline");

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: "domcontentloaded" });

  const mobileFooter = await page.evaluate(() => {
    const routes = document.querySelector(".simpaths-home-paths__routes");
    const citation = document.querySelector(".simpaths-home-citation-band__inner");
    const inner = document.querySelector(".md-footer-meta__inner");
    const meta = document.querySelector(".md-footer-meta");
    const copyright = document.querySelector(".md-copyright");

    return {
      pathColumns: getComputedStyle(routes).gridTemplateColumns.split(" ").length,
      citationColumns: getComputedStyle(citation).gridTemplateColumns.split(" ").length,
      overflow: document.documentElement.scrollWidth - window.innerWidth,
      direction: getComputedStyle(inner).flexDirection,
      height: meta.getBoundingClientRect().height,
      copyrightWidth: copyright.getBoundingClientRect().width
    };
  });

  expect(mobileFooter.pathColumns).toBe(1);
  expect(mobileFooter.citationColumns).toBe(1);
  expect(mobileFooter.overflow).toBeLessThanOrEqual(8);
  expect(mobileFooter.direction).toBe("column");
  expect(mobileFooter.height).toBeLessThan(260);
  expect(mobileFooter.copyrightWidth).toBeGreaterThan(300);
});

test("homepage hero is complete and stable at first paint", async ({ page }) => {
  await page.addInitScript(() => {
    window.__simpathsLayoutShifts = [];
    new PerformanceObserver(list => {
      window.__simpathsLayoutShifts.push(...list.getEntries().filter(entry => !entry.hadRecentInput));
    }).observe({ type: "layout-shift", buffered: true });
  });
  const heroLogoRequests = [];
  await page.route("**/assets/fonts/Inter.woff2", async route => {
    await new Promise(resolve => setTimeout(resolve, 700));
    await route.continue();
  });
  await page.route("**/assets/images/homepage-hero-logo-dark.svg*", route => {
    heroLogoRequests.push(route.request().url());
    return route.abort();
  });
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/", { waitUntil: "domcontentloaded" });

  const logo = page.locator(".simpaths-home-hero__logo");
  await expect(logo).toHaveCount(1);
  await expect(logo).toHaveJSProperty("tagName", "svg");
  await expect(logo.locator("path")).not.toHaveCount(0);

  const initial = await page.evaluate(() => {
    const rect = selector => {
      const box = document.querySelector(selector).getBoundingClientRect();
      return [box.x, box.y, box.width, box.height].map(Math.round);
    };
    return {
      logo: rect(".simpaths-home-hero__logo"),
      title: rect(".simpaths-home-hero__title"),
      hero: rect(".simpaths-home-hero")
    };
  });

  await page.waitForTimeout(900);
  const settled = await page.evaluate(() => {
    const rect = selector => {
      const box = document.querySelector(selector).getBoundingClientRect();
      return [box.x, box.y, box.width, box.height].map(Math.round);
    };
    return {
      logo: rect(".simpaths-home-hero__logo"),
      title: rect(".simpaths-home-hero__title"),
      hero: rect(".simpaths-home-hero")
    };
  });

  expect(settled).toEqual(initial);
  expect(initial.logo[2] / initial.logo[3]).toBeCloseTo(514 / 256, 1);
  expect(heroLogoRequests).toEqual([]);
  const shifts = await page.evaluate(() => window.__simpathsLayoutShifts.map(entry => ({
    value: entry.value,
    sources: entry.sources.map(source => ({
      node: source.node?.className || source.node?.tagName,
      previous: source.previousRect.toJSON(),
      current: source.currentRect.toJSON()
    }))
  })));
  expect(shifts).toEqual([]);
});

test("documentation masthead integrates the SimPaths mark", async ({ page }) => {
  const logoRequests = [];
  await page.route("**/assets/images/documentation-logo-mark*.svg", route => {
    logoRequests.push(route.request().url());
    return route.abort();
  });
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/documentation/", { waitUntil: "domcontentloaded" });

  const desktop = await page.evaluate(() => {
    const masthead = document.querySelector(".docs-index__masthead");
    const markElement = document.querySelector(".docs-index__mark");
    const mark = markElement.getBoundingClientRect();
    const title = masthead.querySelector("h1").getBoundingClientRect();
    const intro = masthead.querySelector(".docs-index__intro").getBoundingClientRect();
    const lightImage = masthead.querySelector(".docs-index__mark-image--light");
    const darkImage = masthead.querySelector(".docs-index__mark-image--dark");

    return {
      title: masthead.querySelector("h1").textContent.trim(),
      fallbackTitles: [...document.querySelectorAll(".md-content__inner > h1")].map((item) =>
        item.textContent.trim()
      ),
      columns: getComputedStyle(masthead).gridTemplateColumns.split(" ").length,
      markWidth: mark.width,
      markBackground: getComputedStyle(markElement).backgroundColor,
      markBeforeTitle: mark.right < title.left,
      introAlignedWithTitle: Math.abs(intro.left - title.left) < 1,
      lightImageInline: lightImage.tagName === "svg" && lightImage.querySelectorAll("path").length > 0,
      lightImageAspectRatio: lightImage.viewBox.baseVal.width / lightImage.viewBox.baseVal.height,
      lightImageDisplay: getComputedStyle(lightImage).display,
      darkImageDisplay: getComputedStyle(darkImage).display
    };
  });

  expect(desktop.title).toBe("SimPaths Documentation");
  expect(desktop.fallbackTitles).toEqual([]);
  expect(desktop.columns).toBe(2);
  expect(desktop.markWidth).toBeGreaterThanOrEqual(110);
  expect(desktop.markWidth).toBeLessThanOrEqual(140);
  expect(desktop.markBackground).toBe("rgb(255, 255, 255)");
  expect(desktop.markBeforeTitle).toBe(true);
  expect(desktop.introAlignedWithTitle).toBe(true);
  expect(desktop.lightImageInline).toBe(true);
  expect(desktop.lightImageAspectRatio).toBeGreaterThan(1.8);
  expect(desktop.lightImageAspectRatio).toBeLessThan(1.9);
  expect(desktop.lightImageDisplay).toBe("block");
  expect(desktop.darkImageDisplay).toBe("none");

  const darkMode = await page.evaluate(() => {
    document.documentElement.dataset.mdColorScheme = "slate";
    const darkImage = document.querySelector(".docs-index__mark-image--dark");
    return {
      light: getComputedStyle(document.querySelector(".docs-index__mark-image--light")).display,
      dark: getComputedStyle(darkImage).display,
      darkImageInline: darkImage.tagName === "svg" && darkImage.querySelectorAll("path").length > 0,
      darkImageAspectRatio: darkImage.viewBox.baseVal.width / darkImage.viewBox.baseVal.height
    };
  });

  expect(darkMode.light).toBe("none");
  expect(darkMode.dark).toBe("block");
  expect(darkMode.darkImageInline).toBe(true);
  expect(darkMode.darkImageAspectRatio).toBeGreaterThan(1.8);
  expect(darkMode.darkImageAspectRatio).toBeLessThan(1.9);

  // Also cover Material's in-place navigation, not only a fresh page load.
  await page.goto("/", { waitUntil: "domcontentloaded" });
  await page.locator(".md-tabs").getByRole("link", { name: "Documentation", exact: true }).click();
  await expect(page).toHaveURL(/\/documentation\/$/);
  await expect(page.locator(".docs-index__mark svg:visible")).toHaveCount(1);
  await expect(page.locator(".docs-index__mark img")).toHaveCount(0);

  await page.setViewportSize({ width: 390, height: 844 });
  await page.reload({ waitUntil: "domcontentloaded" });

  const mobile = await page.evaluate(() => {
    const masthead = document.querySelector(".docs-index__masthead");
    const mark = document.querySelector(".docs-index__mark").getBoundingClientRect();
    const title = masthead.querySelector("h1").getBoundingClientRect();

    return {
      columns: getComputedStyle(masthead).gridTemplateColumns.split(" ").length,
      markAboveTitle: mark.bottom < title.top,
      alignedLeft: Math.abs(mark.left - title.left) < 1,
      overflow: document.documentElement.scrollWidth - window.innerWidth
    };
  });

  expect(mobile.columns).toBe(1);
  expect(mobile.markAboveTitle).toBe(true);
  expect(mobile.alignedLeft).toBe(true);
  expect(mobile.overflow).toBe(0);
  expect(logoRequests).toEqual([]);
});

test("research citation uses restrained hierarchy and natural alignment", async ({ page }) => {
  await page.goto("/research/", { waitUntil: "domcontentloaded" });

  const referencePaper = page.locator(".research-page .research-primary");
  const presentation = await referencePaper.evaluate((element) => {
    const styles = getComputedStyle(element);
    const marker = getComputedStyle(element, "::before");
    const titleLink = element.querySelector("h3 a");

    return {
      borderTopWidth: styles.borderTopWidth,
      borderBottomWidth: styles.borderBottomWidth,
      borderLeftWidth: styles.borderLeftWidth,
      markerContent: marker.content,
      metadataAlignment: getComputedStyle(
        element.querySelector(".research-publication__source")
      ).textAlign,
      titleDecoration: getComputedStyle(titleLink).textDecorationLine
    };
  });

  expect(presentation).toEqual({
    borderTopWidth: "0px",
    borderBottomWidth: "0px",
    borderLeftWidth: "2px",
    markerContent: "none",
    metadataAlignment: "left",
    titleDecoration: "none"
  });
  await expect(page.locator(".research-publications .research-publication")).toHaveCount(6);
});

test("funding uses a compact linked ledger without changing grant details", async ({ page }, testInfo) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto("/funding/");
  await page.evaluate(() => document.fonts.ready);
  await expect(page.locator(".md-search__output")).toBeHidden();
  const expected = [
  [
    {
      "title": "Evaluation of the health impacts of Universal Credit: a mixed methods study",
      "years": "2021-2026",
      "funder": "NIHR"
    },
    {
      "title": "Greater Essex Health Determinants Research Collaboration (HDRC)",
      "years": "2022-2027",
      "funder": "NIHR"
    },
    {
      "title": "Sustainable Welfare: Rethinking the roles of Family, Market and State (SUSTAINWELL)",
      "years": "2023-2027",
      "funder": "Horizon Europe"
    },
    {
      "title": "Policy Modelling for Health",
      "years": "2024-2028",
      "funder": "UKRI PHI"
    },
    {
      "title": "WELLSIM: A life course microsimulation perspective on multi-dimensional well-being for five European countries",
      "years": "2025-2028",
      "funder": "CHANSE/NORFACE"
    },
    {
      "title": "Evaluating the impact of major income support policies on health inequalities across the life course: a micro-macro linked modelling study (MicMac)",
      "years": "2025-2028",
      "funder": "NIHR"
    }
  ],
  [
    {
      "title": "Investigating Economic Insecurity: A Microsimulation Approach",
      "years": "2019-2021",
      "funder": "INAPP"
    },
    {
      "title": "Understanding the impacts of income and welfare policy responses to COVID-19 on inequalities in mental health: A microsimulation model",
      "years": "2021-2022",
      "funder": "Health Foundation"
    },
    {
      "title": "Caring Over the Lifecycle: the Roles of Families and Welfare States Today and Into the Future",
      "years": "2021-2024",
      "funder": "JPI"
    },
    {
      "title": "Health Equity of Economic Determinants (HEED): A Pan-European Microsimulation model for Health impacts of Income and Social Security Policies",
      "years": "2021-2025",
      "funder": "ERC"
    },
    {
      "title": "Overlapping crises: (Re)shaping the future of regional labour markets (OVERLAP)",
      "years": "2023-2025",
      "funder": "ESPON"
    }
  ]
];
  const grants = await page.locator(".funding-panel").evaluateAll(panels => panels.map(panel =>
    [...panel.querySelectorAll("li")].map(item => {
      return Object.fromEntries(["title", "years", "funder"].map(field =>
        [field, item.querySelector(".funding-" + field).textContent.trim()]));
    })
  ));
  expect(grants).toEqual(expected);
  const destinations = await page.locator(".funding-entry").evaluateAll(entries =>
    entries.map(entry => entry.href)
  );
  expect(destinations).toEqual([
    "https://www.iser.essex.ac.uk/research/projects/evaluation-of-the-health-impacts-of-universal-credit-a-mixed-methods-study",
    "https://www.hdrcgreateressex.org/health-determinants-research-collaboration-greater-essex",
    "https://www.iser.essex.ac.uk/research/projects/sustainable-welfare-rethinking-the-roles-of-family-market-and-state-sustainwell",
    "https://www.phiuk.org/policy-modelling-for-health",
    "https://www.iser.essex.ac.uk/research/projects/wellsim-a-life-course-microsimulation-perspective-on-multi-dimensional-well-being-for-five-european-countries",
    "https://fundingawards.nihr.ac.uk/award/NIHR168008",
    "https://www.iser.essex.ac.uk/research/projects/investigating-economic-insecurity-through-microsimulation",
    "https://www.iser.essex.ac.uk/research/projects/understanding-the-impacts-of-income-and-welfare-policy-responses-to-covid-19-on-inequalities-in-mental-health-a-microsimulation-model",
    "https://www.iser.essex.ac.uk/research/projects/caring-over-the-lifecycle-the-roles-of-families-and-welfare-states-today-and-into-the-future-wellcare",
    "https://www.iser.essex.ac.uk/research/projects/health-equity-of-economic-determinants-heed",
    "https://www.iser.essex.ac.uk/research/projects/overlapping-crises-reshaping-the-future-of-regional-labour-markets-overlap"
  ]);
  await expect(page.locator(".funding-panel--current h3")).toHaveCount(6);
  await expect(page.locator(".funding-panel--past h3")).toHaveCount(5);
  await expect(page.locator(".funding-summary dd")).toHaveText(["6", "5", "2019-2028"]);
  await expect(page.locator(".funding-summary dt")).toHaveText(["Current grants", "Completed", "Span"]);
  await expect(page.locator(".funding-focus")).toHaveCount(0);
  await expect(page.locator(".funding-page")).not.toContainText("ModESHI");
  const layout = () => page.evaluate(() => {
    const styles = selector => getComputedStyle(document.querySelector(selector));
    const columns = selector => styles(selector).gridTemplateColumns.split(" ").length;
    return {
      summaryColumns: columns(".funding-summary"),
      entryColumns: columns(".funding-entry"),
      actionColumns: columns(".funding-actions"),
      panelBackground: styles(".funding-panel--current").backgroundColor,
      entryBackground: styles(".funding-entry").backgroundColor,
      entryColor: styles(".funding-entry").color,
      titleColor: styles(".funding-title").color,
      summaryTopRule: styles(".funding-summary").borderTopWidth,
      summaryBottomRule: styles(".funding-summary").borderBottomWidth,
      listTopRule: styles(".funding-list").borderTopWidth,
      rowBottomRule: styles(".funding-list li").borderBottomWidth,
      titleWrap: styles(".funding-title").textWrap,
      pagerDisplay: styles(".md-footer__inner").display,
      metadataFirst: [...document.querySelectorAll(".funding-entry")].every(entry =>
        entry.firstElementChild.classList.contains("funding-meta")),
      fullWidthRows: [...document.querySelectorAll(".funding-list")].every(list =>
        [...list.children].every(item =>
          Math.abs(item.getBoundingClientRect().width - list.getBoundingClientRect().width) < 1)),
      overflow: document.documentElement.scrollWidth - window.innerWidth
    };
  });
  expect(await layout()).toMatchObject({
    summaryColumns: 3, entryColumns: 3, actionColumns: 2,
    panelBackground: "rgba(0, 0, 0, 0)", entryBackground: "rgba(0, 0, 0, 0)",
    entryColor: "rgb(36, 42, 49)", titleColor: "rgb(36, 42, 49)",
    summaryTopRule: "1px", summaryBottomRule: "1px", listTopRule: "1px", rowBottomRule: "1px",
    titleWrap: "wrap", pagerDisplay: "none", metadataFirst: true, fullWidthRows: true, overflow: 0
  });
  const firstGrant = page.locator(".funding-entry").first();
  await firstGrant.hover();
  await expect(firstGrant).not.toHaveCSS("background-color", "rgba(0, 0, 0, 0)");
  await firstGrant.focus();
  await expect(firstGrant).toHaveCSS("outline-style", "solid");
  await page.screenshot({ path: testInfo.outputPath("funding-desktop.png"), fullPage: true, animations: "disabled" });
  await page.setViewportSize({ width: 900, height: 900 });
  expect(await layout()).toMatchObject({entryColumns: 3, actionColumns: 2, overflow: 0});
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    expect(await layout()).toMatchObject({
      summaryColumns: 3, entryColumns: 2, actionColumns: 1,
      metadataFirst: true, fullWidthRows: true, overflow: 0
    });
  }
  await page.screenshot({ path: testInfo.outputPath("funding-mobile.png"), fullPage: true, animations: "disabled" });
});

test("equations render without webfont-dependent blank states", async ({ page }) => {
  await page.goto("/overview/modules/investment-income/#capital-income", {
    waitUntil: "domcontentloaded"
  });

  const expressions = page.locator(".module-detail .arithmatex");
  const displayEquation = expressions.nth(1).locator('mjx-container[jax="SVG"] svg');

  await expect(expressions).toHaveCount(6);
  await expect(displayEquation).toBeVisible();
  await expect(page.locator('mjx-container[jax="CHTML"]')).toHaveCount(0);
  await expect(page.locator("mjx-merror")).toHaveCount(0);
});

test("simulated modules is a single collapsible model branch", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "The primary sidebar becomes a drawer on mobile.");

  await page.goto("/overview/", { waitUntil: "domcontentloaded" });

  const branch = page.locator(
    ".md-sidebar--primary .md-nav__item--section.sp-modules-branch"
  );
  const toggle = branch.locator(":scope > input.md-nav__toggle");
  const childNavigation = branch.locator(":scope > nav.md-nav");

  await expect(toggle).not.toBeChecked();
  await expect(childNavigation).toBeHidden();

  await branch.locator(":scope > label.md-nav__link").click();
  await expect(toggle).toBeChecked();
  await expect(childNavigation).toBeVisible();

  await page.goto("/overview/modules/family-composition/", { waitUntil: "domcontentloaded" });
  await expect(toggle).toBeChecked();
  await expect(branch.getByRole("link", { name: "Family Composition" })).toHaveClass(/md-nav__link--active/);
});

test("documentation filter stays integrated and functional", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "The primary sidebar becomes a drawer on mobile.");

  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/documentation/", { waitUntil: "domcontentloaded" });

  const sidebar = page.locator(".md-sidebar--primary");
  const filter = sidebar.getByPlaceholder("Filter pages");

  await expect(sidebar.getByText("Browse documentation", { exact: true })).toHaveCount(0);
  await expect(filter).toBeVisible();

  const contentEdges = await page.evaluate(() => {
    const intro = document.querySelector(".docs-index__intro");
    const introRange = document.createRange();
    introRange.selectNodeContents(intro);
    const visibleIntroRight = Math.max(
      ...[...introRange.getClientRects()].map((rect) => rect.right),
    );
    const cardListRights = [...document.querySelectorAll(".docs-index__card-list")]
      .map((element) => element.getBoundingClientRect().right);

    return { visibleIntroRight, cardListRights };
  });

  for (const cardListRight of contentEdges.cardListRights) {
    expect(Math.abs(cardListRight - contentEdges.visibleIntroRight)).toBeLessThanOrEqual(12);
  }

  await filter.fill("Regression Library");
  await expect(sidebar.getByRole("link", { name: "Regression Library" })).toBeVisible();
  await expect(sidebar.getByRole("link", { name: "Environment Setup" })).toBeHidden();

  await sidebar.getByRole("button", { name: "Clear sidebar filter" }).click();
  await expect(sidebar.getByRole("link", { name: "Environment Setup" })).toBeVisible();
});

test("long documentation navigation remains clear of the footer", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "The primary sidebar becomes a drawer on mobile.");

  await page.goto("/jasmine-reference/regression-library/", { waitUntil: "domcontentloaded" });
  await page.locator("footer").scrollIntoViewIfNeeded();

  await expect(page.getByRole("link", { name: "Matching Library" }).last()).toBeVisible();
  await expect(page.getByRole("link", { name: "Saving Outputs" }).last()).toBeVisible();

  const overlapPixels = await page.evaluate(() => {
    const footer = document.querySelector("footer");
    const sidebarRail = document.querySelector(".md-sidebar--primary .md-sidebar__scrollwrap");
    const footerTop = footer.getBoundingClientRect().top;
    const sidebarBottom = sidebarRail.getBoundingClientRect().bottom;
    return Math.max(0, Math.round(sidebarBottom - footerTop));
  });

  expect(overlapPixels).toBeLessThanOrEqual(1);
});

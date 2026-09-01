import { expect, test } from "@playwright/test";

const routes = [
  ["home", "/"],
  ["model", "/overview/"],
  ["documentation", "/documentation/"],
  ["roadmap", "/overview/roadmap/"],
  ["research", "/research/"],
  ["funding", "/funding/"],
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
  await expect(page.getByRole("heading", { name: "Model foundations", exact: true })).toBeVisible();
  await expect(
    page.locator(".roadmap-stage--now").getByRole("heading", {
      name: "Wealth across the life course",
      exact: true
    })
  ).toBeVisible();
  await expect(page.locator(".roadmap-stage")).toHaveCount(4);
  await expect(page.locator(".roadmap-item")).toHaveCount(16);
  await expect(page.locator(".roadmap-impact")).toHaveCount(0);
  await expect(page.locator(".roadmap-page > ol")).toHaveCount(0);
  await expect(page.locator('.roadmap-meta a[href*="/issues/"]')).toHaveCount(39);

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
    itemAlignment: [...document.querySelectorAll(".roadmap-item > p:not(.roadmap-meta)")].map(
      (paragraph) => getComputedStyle(paragraph).textAlign
    ),
    overflow: document.documentElement.scrollWidth - window.innerWidth
  }));

  expect(presentation.headingRules.every((width) => width === "0px")).toBe(true);
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
    const title = getComputedStyle(document.querySelector(".md-header__title"));
    const overlay = getComputedStyle(document.querySelector(".md-search__overlay"));
    return {
      searchWidth: search.width,
      innerWidth: inner.width,
      rightEdgeDifference: Math.abs(search.right - output.right),
      titleOpacity: title.opacity,
      overlayBackground: overlay.backgroundColor
    };
  });

  expect(openSearch.searchWidth).toBeGreaterThan(740);
  expect(openSearch.searchWidth).toBeLessThanOrEqual(760);
  expect(Math.abs(openSearch.searchWidth - openSearch.innerWidth)).toBeLessThanOrEqual(1);
  expect(openSearch.rightEdgeDifference).toBeLessThanOrEqual(1);
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
  await expect(page.locator(".simpaths-home-paths__route")).toHaveCount(4);
  await expect(page.locator(".simpaths-home-paths__links a")).toHaveCount(12);
  await expect(page.locator(".simpaths-home-intro-band__access")).toHaveCount(2);

  const presentation = await page.evaluate(() => {
    const paths = document.querySelector(".simpaths-home-paths");
    const routes = document.querySelector(".simpaths-home-paths__routes");
    const routeItems = [...document.querySelectorAll(".simpaths-home-paths__route")];
    const band = document.querySelector(".simpaths-home-research-band");
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
    const inner = document.querySelector(".md-footer-meta__inner");
    const meta = document.querySelector(".md-footer-meta");
    const copyright = document.querySelector(".md-copyright");

    return {
      pathColumns: getComputedStyle(routes).gridTemplateColumns.split(" ").length,
      overflow: document.documentElement.scrollWidth - window.innerWidth,
      direction: getComputedStyle(inner).flexDirection,
      height: meta.getBoundingClientRect().height,
      copyrightWidth: copyright.getBoundingClientRect().width
    };
  });

  expect(mobileFooter.pathColumns).toBe(1);
  expect(mobileFooter.overflow).toBeLessThanOrEqual(8);
  expect(mobileFooter.direction).toBe("column");
  expect(mobileFooter.height).toBeLessThan(260);
  expect(mobileFooter.copyrightWidth).toBeGreaterThan(300);
});

test("documentation masthead integrates the SimPaths mark", async ({ page }) => {
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
      lightImageLoaded: lightImage.complete && lightImage.naturalWidth > 0,
      lightImageSource: new URL(lightImage.currentSrc).pathname,
      lightImageAspectRatio: lightImage.naturalWidth / lightImage.naturalHeight,
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
  expect(desktop.lightImageLoaded).toBe(true);
  expect(desktop.lightImageSource).toBe("/assets/images/documentation-logo-mark.svg");
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
      darkImageSource: new URL(darkImage.currentSrc).pathname,
      darkImageAspectRatio: darkImage.naturalWidth / darkImage.naturalHeight
    };
  });

  expect(darkMode.light).toBe("none");
  expect(darkMode.dark).toBe("block");
  expect(darkMode.darkImageSource).toBe("/assets/images/documentation-logo-mark-dark.svg");
  expect(darkMode.darkImageAspectRatio).toBeGreaterThan(1.8);
  expect(darkMode.darkImageAspectRatio).toBeLessThan(1.9);

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
  await expect(page.locator(".research-publications .research-publication")).toHaveCount(5);
});

test("funding distinguishes active programmes from the completed archive", async ({ page }) => {
  await page.setViewportSize({ width: 1512, height: 900 });
  await page.goto("/funding/", { waitUntil: "domcontentloaded" });

  const desktop = await page.evaluate(() => {
    const grid = document.querySelector(".funding-grid");
    const current = document.querySelector(".funding-panel--current");
    const past = document.querySelector(".funding-panel--past");
    const currentList = current.querySelector(".funding-list");
    const pastList = past.querySelector(".funding-list");
    const currentEntries = [...currentList.children];
    const pastEntries = [...pastList.children];
    const allEntries = [...currentEntries, ...pastEntries];
    const firstTitle = currentEntries[0].querySelector(".funding-title");
    const titleStyles = getComputedStyle(firstTitle);
    const pager = document.querySelector(".md-footer__inner");

    return {
      gridDisplay: getComputedStyle(grid).display,
      gridGap: parseFloat(getComputedStyle(grid).rowGap),
      currentColumns: getComputedStyle(currentList).gridTemplateColumns.split(" ").length,
      currentBackground: getComputedStyle(current).backgroundColor,
      currentEntryBackgrounds: currentEntries.map((entry) => getComputedStyle(entry).backgroundColor),
      currentMetadataAboveTitle: currentEntries.every((entry) => {
        const title = entry.querySelector(".funding-title").getBoundingClientRect();
        const metadata = entry.querySelector(".funding-meta").getBoundingClientRect();
        return metadata.bottom <= title.top;
      }),
      pastColumns: getComputedStyle(pastList).gridTemplateColumns.split(" ").length,
      pastBackground: getComputedStyle(pastList).backgroundColor,
      pastItemColumns: pastEntries.map((entry) => getComputedStyle(entry).gridTemplateColumns.split(" ").length),
      pastMetadataLeftOfTitle: pastEntries.every((entry) => {
        const title = entry.querySelector(".funding-title").getBoundingClientRect();
        const metadata = entry.querySelector(".funding-meta").getBoundingClientRect();
        return metadata.right < title.left;
      }),
      panelPositions: [current, past].map((panel) => Math.round(panel.getBoundingClientRect().top)),
      entryTopBorders: allEntries.map((entry) => getComputedStyle(entry).borderTopWidth),
      currentCount: currentEntries.length,
      pastCount: pastEntries.length,
      titleFontSize: parseFloat(titleStyles.fontSize),
      titleFontWeight: parseFloat(titleStyles.fontWeight),
      pagerDisplay: pager ? getComputedStyle(pager).display : "missing"
    };
  });

  expect(desktop.gridDisplay).toBe("grid");
  expect(desktop.gridGap).toBeGreaterThanOrEqual(60);
  expect(desktop.currentColumns).toBe(2);
  expect(desktop.currentBackground).toBe("rgb(240, 237, 230)");
  expect(new Set(desktop.currentEntryBackgrounds)).toEqual(new Set(["rgb(255, 254, 250)"]));
  expect(desktop.currentMetadataAboveTitle).toBe(true);
  expect(desktop.pastColumns).toBe(1);
  expect(desktop.pastBackground).toBe("rgb(246, 244, 239)");
  expect(desktop.pastItemColumns.every((columns) => columns === 2)).toBe(true);
  expect(desktop.pastMetadataLeftOfTitle).toBe(true);
  expect(desktop.panelPositions[1]).toBeGreaterThan(desktop.panelPositions[0]);
  expect(desktop.currentCount).toBe(6);
  expect(desktop.pastCount).toBe(5);
  expect(new Set(desktop.entryTopBorders)).toEqual(new Set(["0px"]));
  expect(desktop.titleFontSize).toBeGreaterThanOrEqual(16);
  expect(desktop.titleFontSize).toBeLessThanOrEqual(19);
  expect(desktop.titleFontWeight).toBeGreaterThanOrEqual(500);
  expect(desktop.titleFontWeight).toBeLessThanOrEqual(620);
  expect(desktop.pagerDisplay).toBe("none");

  await page.setViewportSize({ width: 390, height: 844 });

  const mobile = await page.evaluate(() => {
    const currentList = document.querySelector(".funding-panel--current .funding-list");
    const pastEntry = document.querySelector(".funding-panel--past .funding-list > li");
    const metadata = pastEntry.querySelector(".funding-meta").getBoundingClientRect();
    const title = pastEntry.querySelector(".funding-title").getBoundingClientRect();

    return {
      currentColumns: getComputedStyle(currentList).gridTemplateColumns.split(" ").length,
      pastItemColumns: getComputedStyle(pastEntry).gridTemplateColumns.split(" ").length,
      metadataAboveTitle: metadata.bottom <= title.top,
      overflow: document.documentElement.scrollWidth - window.innerWidth
    };
  });

  expect(mobile.currentColumns).toBe(1);
  expect(mobile.pastItemColumns).toBe(1);
  expect(mobile.metadataAboveTitle).toBe(true);
  expect(mobile.overflow).toBe(0);
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

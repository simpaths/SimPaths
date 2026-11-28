import { expect, test } from "@playwright/test";

const searchOpener = (page) => page.locator(page.viewportSize().width < 960
  ? '.md-header__button[data-sp-toggle="search"]'
  : '.md-search__form > button[data-sp-toggle="search"]');
const openSearch = async (page) => {
  await searchOpener(page).click();
  await expect(page.locator(".md-search__input")).toBeFocused();
};

for (const colorScheme of ["light", "dark"]) {
  test(`suggested pages give way to search results and return on clear (${colorScheme})`, async ({ page }, testInfo) => {
    await page.emulateMedia({ colorScheme });
    await page.goto("/documentation/");
    await openSearch(page);
    const suggestions = page.getByRole("navigation", { name: "Suggested pages" });
    const input = page.locator(".md-search__input");
    await expect(suggestions.getByRole("link")).toHaveCount(4);
    await expect(suggestions.getByRole("link").last()).toBeInViewport();
    await expect(page.locator(".md-search-result")).toBeHidden();
    expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: testInfo.outputPath("search-suggestions.png") });

    await input.pressSequentially("regression");
    await expect(suggestions).toBeHidden();
    await expect(page.locator(".md-search-result__link").first()).toBeVisible();
    await expect(page.locator(".md-search-result__link").nth(3)).toBeInViewport();
    await expect(page.locator(".md-search-result pre, .md-search-result code")).toHaveCount(0);
    const excerpts = await page.locator(".sp-search-excerpt").evaluateAll(nodes => nodes.map(node => ({
      height: node.getBoundingClientRect().height,
      line: parseFloat(getComputedStyle(node).lineHeight)
    })));
    expect(excerpts.every(({ height, line }) => height <= line * 2 + 1)).toBe(true);
    await page.screenshot({ path: testInfo.outputPath("search-results.png") });
    const resultHeight = (await page.locator('.md-search__output').boundingBox()).height;
    await input.press("ControlOrMeta+A");
    await input.pressSequentially("noresultsxyz987");
    await expect(page.locator(".md-search-result__meta")).toContainText("No matching");
    if (testInfo.project.name === "desktop") {
      expect(Math.abs((await page.locator('.md-search__output').boundingBox()).height - resultHeight)).toBeLessThanOrEqual(1);
    }
    await expect(suggestions).toBeHidden();
    await page.getByRole("button", { name: "Clear", exact: true }).click();
    await expect(input).toHaveValue("");
    await expect(suggestions).toBeVisible();
    await expect(page.locator(".md-search-result")).toBeHidden();
    await input.fill("   ");
    await expect(suggestions).toBeVisible();
    await input.press("Enter");
    await expect(page).toHaveURL(/\/documentation\/$/);
    await input.press("Escape");
    await expect(suggestions).toBeHidden();
    await expect(searchOpener(page)).toBeFocused();
  });
}

test("suggestion links and keyboard navigation survive navigation to a deeper page", async ({ page }) => {
  await page.goto("/documentation/");
  const input = page.locator(".md-search__input");
  const suggestions = page.getByRole("navigation", { name: "Suggested pages" });
  await openSearch(page);
  await input.press("ArrowDown");
  await expect(suggestions.getByRole("link").first()).toBeFocused();
  await page.keyboard.press("Enter");
  await expect(page).toHaveURL(/\/getting-started\/first-simulation\/$/);
  await expect(page.locator("#__search")).not.toBeChecked();

  await openSearch(page);
  await suggestions.getByRole("link", { name: /^First-run troubleshooting/ }).click();
  await expect(page).toHaveURL(/\/getting-started\/first-simulation\/#common-first-run-problems$/);
  await expect(page.locator("#__search")).not.toBeChecked();
  await expect(page.getByRole("heading", { name: /^Common first-run problems/ })).toBeInViewport();

  await openSearch(page);
  await input.press("ArrowDown");
  await page.keyboard.press("ArrowUp");
  await expect(input).toBeFocused();
  await suggestions.getByRole("link", { name: /^Model modules/ }).click();
  await expect(page).toHaveURL(/\/overview\/#simulated-modules$/);
  await expect(page.getByRole("heading", { name: /^Population and model components/ })).toBeInViewport();
  await openSearch(page);
  await suggestions.getByRole("link", { name: /^Input data/ }).click();
  await expect(page).toHaveURL(/\/getting-started\/data\/$/);
  await expect(page.getByRole("heading", { name: "Input Data", exact: true })).toBeVisible();
});

test("spelling recovery preserves exact terms, prefixes and useful multiword results", async ({ page }) => {
  await page.goto("/documentation/");
  await openSearch(page);
  const input = page.locator(".md-search__input");
  const status = page.getByRole("status");
  const links = page.locator(".md-search-result__link");
  await input.fill("memory");
  await expect(status).toContainText("matching sections");
  const memory = await links.evaluateAll(nodes => nodes.map(node => node.href));
  await input.fill("memroy");
  await expect(status).toContainText('Showing results for “memory”');
  expect(await links.evaluateAll(nodes => nodes.map(node => node.href))).toEqual(memory);
  await expect(input).toHaveValue("memroy");
  await expect(page.getByRole("link", { name: "Share search" })).toHaveAttribute("href", /q=memroy$/);

  for (const query of ["SimPathsModel", "regre", "MultiKeyCoefficientMap", '"regression coefficients"']) {
    await input.fill(query);
    await expect(status).toContainText("matching");
    await expect(links.first()).toBeVisible();
    await expect(status).not.toContainText("Showing results for");
    await expect(input).toHaveValue(query);
  }
  await input.fill("regresion coefficients");
  await expect(status).toContainText('Showing results for “regression coefficients”');
  await expect(links.first()).toContainText(/coefficients/i);
  await input.fill("zzxyqpx");
  await expect(status).toContainText("No matching results");
  await expect(links).toHaveCount(0);
});

test("shared queries, section navigation and keyboard controls work together", async ({ page }) => {
  await page.goto("/documentation/?q=memroy");
  const input = page.locator(".md-search__input");
  await expect(input).toBeFocused();
  await expect(input).toHaveValue("memroy");
  await expect(page.getByRole("status")).toContainText('Showing results for “memory”');
  await input.fill("Common first-run problems");
  const first = page.locator(".md-search-result__link").first();
  await expect(first).toHaveAttribute("href", /#common-first-run-problems$/);
  await input.press("ArrowDown");
  await expect(first).toBeFocused();
  await page.keyboard.press("ArrowUp");
  await expect(input).toBeFocused();
  await input.press("Enter");
  await expect(page).toHaveURL(/first-simulation\/\?h=.+#common-first-run-problems$/);
  await expect(page.locator("#__search")).not.toBeChecked();
  await expect(page.locator("#common-first-run-problems")).toBeInViewport();
  await expect(page.locator("#common-first-run-problems mark").first()).toBeVisible();
  await openSearch(page);
  await input.fill("regression");
  await expect(page.getByRole("status")).toContainText("matching sections");
  await page.getByRole("button", { name: "Show more results" }).click();
  await expect(page.locator(".md-search-result__link").nth(20)).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(page.locator("#__search")).toBeChecked();
  await page.keyboard.press("Escape");
  await expect(searchOpener(page)).toBeFocused();
  await expect(page).not.toHaveURL(/\bq=/);
  expect(page.workers()).toHaveLength(1);
});

test("an unavailable index reports a recoverable error", async ({ page }) => {
  await page.route("**/search/search_index.json", route => route.abort());
  await page.goto("/documentation/");
  await openSearch(page);
  const input = page.locator(".md-search__input");
  await input.fill("memory");
  await expect(page.getByRole("status")).toContainText("Search is unavailable");
  await page.unroute("**/search/search_index.json");
  await input.fill("regression");
  await expect(page.locator(".md-search-result__link").first()).toBeVisible();
  await expect(page.getByRole("status")).toContainText("matching sections");
});

test("slow initialization uses the latest input and clears stale results", async ({ page }) => {
  await page.route("**/search/search_index.json", async route => {
    await new Promise(resolve => setTimeout(resolve, 350));
    await route.continue();
  });
  await page.goto("/documentation/");
  await openSearch(page);
  const input = page.locator(".md-search__input");
  await input.fill("regression");
  await expect(page.getByRole("status")).toHaveText("Searching…");
  await input.fill("memroy");
  await expect(page.getByRole("status")).toContainText('Showing results for “memory”');
  await expect(page.locator(".md-search-result__link").first()).toHaveAttribute("href", /h=memory/);
  await input.fill("noresultsxyz987");
  await expect(page.locator(".md-search-result__link")).toHaveCount(0);
  await page.getByRole("button", { name: "Clear", exact: true }).click();
  await expect(page.getByRole("navigation", { name: "Suggested pages" })).toBeVisible();
  await expect(page.locator(".md-search-result")).toBeHidden();
});

test("search URLs retain a deployment prefix across navigation", async ({ page, context }) => {
  await context.route("**/preview/**", async route => {
    const response = await route.fetch({ url: route.request().url().replace("/preview/", "/"), maxRetries: 2 });
    await route.fulfill({ response });
  });
  await page.goto("/preview/documentation/?q=Common+first-run+problems");
  const first = page.locator(".md-search-result__link").first();
  await expect(first).toHaveAttribute("href", /\/preview\/getting-started\/first-simulation\/.+#common-first-run-problems$/);
  await first.click();
  await expect(page).toHaveURL(/\/preview\/getting-started\/first-simulation\/.+#common-first-run-problems$/);
  await openSearch(page);
  // A prefixed host can use full navigation when its sitemap is not cached.
  await page.locator('.md-search__input').fill('');
  await page.getByRole("link", { name: /^Input data Find/ }).click();
  await expect(page).toHaveURL(/\/preview\/getting-started\/data\/$/);
});

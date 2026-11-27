import { test, expect } from "@playwright/test";

// Most layout checks disable motion; scrolling must also be exercised with it on.
test.use({ contextOptions: { reducedMotion: "no-preference" } });

async function sampleScroll(page, top, duration = 350) {
  return page.evaluate(async ({ top, duration }) => {
    const main = document.querySelector(".md-main");
    const initialHeight = document.documentElement.scrollHeight;
    const initialTop = main.getBoundingClientRect().top + scrollY;
    const expectedY = Math.min(top, Math.max(0, initialHeight - innerHeight));
    const frames = [];
    window.scrollTo(0, top);
    await new Promise(resolve => {
      const start = performance.now();
      const sample = time => {
        frames.push({
          scrollDrift: Math.abs(scrollY - expectedY),
          contentShift: Math.abs(main.getBoundingClientRect().top + scrollY - initialTop),
          heightChange: Math.abs(document.documentElement.scrollHeight - initialHeight)
        });
        if (time - start < duration) requestAnimationFrame(sample);
        else resolve();
      };
      requestAnimationFrame(sample);
    });
    return {
      frames: frames.length,
      scrollDrift: Math.max(...frames.map(frame => frame.scrollDrift)),
      contentShift: Math.max(...frames.map(frame => frame.contentShift)),
      heightChange: Math.max(...frames.map(frame => frame.heightChange)),
      headerHeight: document.querySelector(".md-header").getBoundingClientRect().height
    };
  }, { top, duration });
}

function expectStable(result) {
  expect(result.frames).toBeGreaterThan(1);
  expect(result.scrollDrift, "scroll position must not move without user input").toBeLessThanOrEqual(1);
  expect(result.contentShift, "header motion must not move the page content").toBeLessThan(0.5);
  expect(result.heightChange, "header motion must not change the scrollable height").toBeLessThanOrEqual(1);
}

for (const route of ["/", "/documentation/", "/validation/", "/research/", "/funding/", "/overview/"]) {
  test(`scrolling down and back up keeps the page stable: ${route}`, async ({ page }) => {
    await page.goto(route);
    await page.evaluate(() => document.fonts.ready);
    await expect.poll(() => page.evaluate(() =>
      document.documentElement.style.getPropertyValue("--sp-header-progress")
    )).toBe("0.000");
    const expandedHeight = await page.locator(".md-header").evaluate(element => element.getBoundingClientRect().height);
    for (const top of [80, 40, 240, 60, 0]) {
      const result = await sampleScroll(page, top);
      expectStable(result);
      if (top === 240) expect(result.headerHeight).toBeLessThan(expandedHeight - 2);
      if (top === 0) expect(Math.abs(result.headerHeight - expandedHeight)).toBeLessThan(0.5);
    }
  });
}

test("a short documentation page stays still at the bottom with header motion enabled", async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== "desktop", "Exercise a tall desktop viewport with less than 160px to scroll.");
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.goto("/documentation/");
  await page.evaluate(() => document.fonts.ready);
  const height = await page.evaluate(() => document.documentElement.scrollHeight);
  await page.setViewportSize({ width: 1440, height: height - 56 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight - innerHeight)).toBe(56);
  expectStable(await sampleScroll(page, height, 900));
  expectStable(await sampleScroll(page, 0));
});

test("reduced motion keeps the header full-sized while scrolling", async ({ page }) => {
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.goto("/documentation/");
  await page.evaluate(() => document.fonts.ready);
  const expandedHeight = await page.locator(".md-header").evaluate(element => element.getBoundingClientRect().height);
  for (const top of [80, 240, 0]) {
    const result = await sampleScroll(page, top);
    expectStable(result);
    expect(result.headerHeight).toBe(expandedHeight);
  }
});

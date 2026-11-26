(() => {
  const managedBodyClasses = [
    "sp-page-home",
    "sp-page-docs-index",
    "sp-page-directory",
    "sp-page-funding",
    "sp-page-roadmap",
    "sp-page-validation",
    "sp-no-section-nav",
    "sp-no-toc",
    "sp-tab-model",
    "sp-tab-documentation"
  ];

  let searchToggle = null;
  let updateSearchState = null;

  // One listener for the persistent shell, also used after instant navigation.
  // Spacing, mark and shadow share one scroll progress; stop writing once settled.
  // Reduced motion keeps the full-sized header still, including on restored pages.
  const reducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  let headerFrame = 0;
  let headerProgress = null;
  const updateHeaderState = () => {
    headerFrame = 0;
    const position = reducedMotion.matches ? 0 : Math.min(1, Math.max(0, window.scrollY / 160));
    // Smooth endpoints avoid an abrupt start or stop as the header settles.
    const progress = (position * position * (3 - 2 * position)).toFixed(3);
    if (progress !== headerProgress) {
      document.documentElement.style.setProperty("--sp-header-progress", progress);
      headerProgress = progress;
    }
  };
  const scheduleHeaderState = () => {
    if (!headerFrame) headerFrame = requestAnimationFrame(updateHeaderState);
  };
  window.addEventListener("scroll", scheduleHeaderState, { passive: true });
  window.addEventListener("pageshow", scheduleHeaderState);
  reducedMotion.addEventListener("change", scheduleHeaderState);

  const setSearchState = () => {
    document.body.classList.toggle("sp-search-open", Boolean(searchToggle?.checked));
  };

  const bindSearchState = () => {
    if (searchToggle && updateSearchState) {
      searchToggle.removeEventListener("change", updateSearchState);
    }

    searchToggle = document.querySelector("#__search");
    updateSearchState = setSearchState;

    const searchInput = document.querySelector(".md-search__input");
    if (searchInput) {
      searchInput.placeholder = "Search SimPaths";
    }

    if (searchToggle) {
      searchToggle.addEventListener("change", updateSearchState);
    }

    setSearchState();
  };

  const markNavigationState = () => {
    document.querySelectorAll(".sp-nav-container-active").forEach((container) => {
      container.classList.remove("sp-nav-container-active");
    });
    document.querySelectorAll(".sp-modules-branch").forEach((branch) => {
      branch.classList.remove("sp-modules-branch");
    });

    document.querySelectorAll(".md-nav__container").forEach((container) => {
      const directActiveLink = Array.from(container.children).some(
        (child) => child.matches("a.md-nav__link--active")
      );
      container.classList.toggle("sp-nav-container-active", directActiveLink);
    });

    document
      .querySelectorAll('.md-sidebar--primary a[href*="/overview/modules/"]')
      .forEach((link) => {
        link
          .closest("li.md-nav__item--section.md-nav__item--nested")
          ?.classList.add("sp-modules-branch");
      });
  };

  const updateSiteState = () => {
    const body = document.body;
    const pageRoots = [document.documentElement, body];
    const pageState = document.querySelector("[data-sp-page-classes]");
    const classes = new Set((pageState?.dataset.spPageClasses || "").split(/\s+/));
    managedBodyClasses.forEach((className) => {
      pageRoots.forEach((root) => root.classList.toggle(className, classes.has(className)));
    });

    markNavigationState();
    bindSearchState();
    scheduleHeaderState();
  };

  if (typeof document$ !== "undefined") {
    document$.subscribe(updateSiteState);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", updateSiteState, { once: true });
  } else {
    updateSiteState();
  }
})();

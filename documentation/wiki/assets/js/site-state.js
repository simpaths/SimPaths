(() => {
  const managedBodyClasses = [
    "sp-page-home",
    "sp-page-docs-index",
    "sp-page-funding",
    "sp-page-roadmap",
    "sp-page-validation",
    "sp-reserve-toc-space",
    "sp-tab-model",
    "sp-tab-documentation"
  ];

  let searchToggle = null;
  let updateSearchState = null;

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
    const togglePageClass = (className, active) => {
      pageRoots.forEach((root) => root.classList.toggle(className, active));
    };
    managedBodyClasses.forEach((className) => {
      pageRoots.forEach((root) => root.classList.remove(className));
    });

    togglePageClass("sp-page-home", Boolean(document.querySelector(".simpaths-home-hero")));
    togglePageClass("sp-page-docs-index", Boolean(document.querySelector(".docs-hub--index")));
    togglePageClass("sp-page-funding", Boolean(document.querySelector(".funding-page")));
    togglePageClass("sp-page-roadmap", Boolean(document.querySelector(".roadmap-page")));
    togglePageClass(
      "sp-page-validation",
      Boolean(document.querySelector(".validation-page-marker"))
    );
    togglePageClass(
      "sp-reserve-toc-space",
      Boolean(
        document.querySelector(
          ".model-overview, #user-guide, #getting-started, #jas-mine-architecture"
        )
      )
    );

    const activeTab = document.querySelector(".md-tabs__item--active .md-tabs__link")
      ?.textContent.trim().toLocaleLowerCase();
    togglePageClass("sp-tab-model", activeTab === "model");
    togglePageClass("sp-tab-documentation", activeTab === "documentation");

    markNavigationState();
    bindSearchState();
  };

  if (typeof document$ !== "undefined") {
    document$.subscribe(updateSiteState);
  } else if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", updateSiteState, { once: true });
  } else {
    updateSiteState();
  }
})();

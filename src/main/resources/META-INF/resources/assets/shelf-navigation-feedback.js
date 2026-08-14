(() => {
  const feedback = document.querySelector("[data-shelf-navigation-feedback]");
  if (!feedback) return;

  const showFeedback = () => {
    document.body.classList.add("shelf-navigation-pending");
    document.body.setAttribute("aria-busy", "true");
    feedback.hidden = false;
  };

  document.addEventListener("click", (event) => {
    const link = event.target.closest(".shelf-navigation-link");
    if (
      !link ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      link.target ||
      link.hasAttribute("download") ||
      link.getAttribute("aria-current") === "page"
    ) {
      return;
    }

    showFeedback();
  });

  window.addEventListener("pageshow", () => {
    document.body.classList.remove("shelf-navigation-pending");
    document.body.removeAttribute("aria-busy");
    feedback.hidden = true;
  });
})();

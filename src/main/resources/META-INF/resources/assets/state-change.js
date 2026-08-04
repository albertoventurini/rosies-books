(() => {
  const target = document.querySelector("[data-shelf-target]");
  if (!target) return;

  const groups = document.querySelectorAll("[data-shelf-date-fields]");
  const updateDateFields = () => {
    for (const group of groups) {
      const active = group.dataset.shelfDateFields === target.value;
      group.hidden = !active;
      for (const control of group.querySelectorAll("input, select, textarea")) {
        control.disabled = !active;
      }
    }
  };

  target.addEventListener("change", updateDateFields);
  updateDateFields();
})();

(() => {
  const forms = document.querySelectorAll("[data-library-search]");

  const isSearchable = (value) => {
    const trimmed = value.trim();
    const isbnDigits = trimmed.replace(/[ -]/g, "");
    if (/^[0-9]{6,}$/.test(isbnDigits)) return true;
    return [...trimmed].filter((character) => /\p{L}/u.test(character)).length >= 3;
  };

  forms.forEach((form) => {
    const input = form.querySelector("input[name=q]");
    const submit = form.querySelector("[data-library-search-submit]");
    const update = () => {
      submit.disabled = !isSearchable(input.value);
    };

    input.addEventListener("input", update);
    update();
  });
})();

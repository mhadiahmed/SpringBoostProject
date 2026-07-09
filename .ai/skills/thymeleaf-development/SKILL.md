---
name: thymeleaf-development
package: Thymeleaf
---

# Thymeleaf Development

Load when building server-rendered views with Thymeleaf templates and Spring
MVC controllers.

## Checklist

- Fragments (`th:fragment`) for anything reused across pages (nav, layout
  shell, form field groups) — copy-pasted template blocks are the templating
  equivalent of duplicated code.
- Use `th:object`/`*{field}` binding with a form-backing DTO, not manual
  `${...}` string concatenation into `name`/`value` attributes.
- Escape by default (`th:text`, not `th:utext`) — only use `th:utext` for
  content you've explicitly sanitized, since it skips HTML escaping.
- Validation errors render with `#fields.hasErrors('field')` /
  `#fields.errors('field')` tied to the same `@Valid` DTO the controller
  already validates.

## Controller + template

```java
@GetMapping("/users/{id}/edit")
String editForm(@PathVariable Long id, Model model) {
    model.addAttribute("user", userService.getEditableUser(id));
    return "users/edit";
}

@PostMapping("/users/{id}/edit")
String update(@PathVariable Long id, @Valid @ModelAttribute("user") UserForm form,
              BindingResult result) {
    if (result.hasErrors()) return "users/edit";
    userService.update(id, form);
    return "redirect:/users/" + id;
}
```

```html
<form th:action="@{/users/{id}/edit(id=${user.id})}" th:object="${user}" method="post">
  <input th:field="*{email}" />
  <span th:if="${#fields.hasErrors('email')}" th:errors="*{email}"></span>
</form>
```

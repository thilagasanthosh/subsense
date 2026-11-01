package com.subsense.subscription;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
@Tag(name = "Categories")
class CategoryController {

  private final CategoryRepository categories;

  CategoryController(CategoryRepository categories) {
    this.categories = categories;
  }

  @GetMapping
  List<SubscriptionView.CategoryView> list() {
    return categories.findAllByOrderByNameAsc().stream()
        .map(SubscriptionView.CategoryView::of)
        .toList();
  }
}

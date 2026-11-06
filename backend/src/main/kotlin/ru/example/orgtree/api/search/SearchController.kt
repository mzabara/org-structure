package ru.example.orgtree.api.search

import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.Size
import org.springframework.web.bind.annotation.*
import ru.example.orgtree.service.SearchService

/** Единая точка поиска сохраняет независимые лимиты для людей и подразделений. */
@RestController
@RequestMapping("/api/v1/search")
class SearchController(private val search: SearchService) {
    @GetMapping
    fun search(
        @RequestParam @Size(min = 2, max = 100) q: String,
        @RequestParam(defaultValue = "10") @Min(1) @Max(50) limit: Int,
    ) = search.search(q, limit)
}

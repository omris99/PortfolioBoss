package portfolioboss.api.request;

import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * The body of {@code POST /api/analysis}: which holdings to analyze — empty or missing for every open one, so analyzing
 * everything is {@code {}}. A body even then (AI_ANALYSIS_TODO.md, decision 13): the endpoint accepts JSON only, so a
 * page on another site can't start a paid run with a form.
 */
public record AnalysisRequest(List<@NotNull Long> holdingIds) {
}

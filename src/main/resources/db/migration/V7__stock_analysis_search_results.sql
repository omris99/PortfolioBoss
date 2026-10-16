-- The search results Claude read for each analysis (AI_ANALYSIS_TODO.md, decision 22): the four Tavily searches, only
-- the results that name the stock, exactly as they were sent. Kept so that a fact missing from an analysis can be
-- traced — never found by the search, or found and left out by Claude. NULL for the analyses stored before.
ALTER TABLE stock_analysis ADD COLUMN search_results JSONB;

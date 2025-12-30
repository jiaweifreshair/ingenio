import logging
import json
import httpx
import os
from typing import List, Dict, Any
from pydantic import BaseModel

# Mocking external tools for the prototype
# In production, these would be actual MCP calls or Google Search API calls
class RepoCandidate(BaseModel):
    name: str
    url: str
    description: str
    stars: int = 0
    match_score: int = 0
    analysis_reason: str = ""

class RepoScoutAgent:
    """
    The Scout Agent is responsible for discovering and analyzing external repositories.
    It mimics the behavior of a GitHub MCP server.
    """
    
    def __init__(self, template_manager):
        self.logger = logging.getLogger("RepoScout")
        self.template_manager = template_manager

    async def search_and_analyze(self, requirement: str) -> List[RepoCandidate]:
        """
        Main entry point: Search -> Analyze -> Rank -> AI Evaluate
        """
        self.logger.info(f"🔍 Scout initialized. Target: {requirement}")
        
        # 1. Search Phase
        candidates = await self._search_github_real(requirement)
        
        if not candidates:
            self.logger.warning("Real search failed or returned no results. Falling back to Mock.")
            candidates = self._mock_search_github(requirement)
            
        self.logger.info(f"Found {len(candidates)} potential candidates.")

        # 2. Analysis Phase
        results = []
        for cand in candidates:
            self.logger.info(f"🕵️ Analyzing candidate: {cand.name}...")
            
            # Simulate "Deep Read" via TemplateManager (Sparse Checkout)
            # Here we just simulate the analysis logic
            score, reason = await self._analyze_match(cand, requirement)
            cand.match_score = score
            cand.analysis_reason = reason
            results.append(cand)
            
            # Emit progress log (in a real async stream)
            self.logger.info(f"✅ Analysis complete for {cand.name}: Score {score}")

        # 3. Sort by heuristic score first
        results.sort(key=lambda x: x.match_score, reverse=True)
        
        # 4. AI Architect Evaluation (Phase 7.3)
        # Only evaluate if we have results
        if results:
            self.logger.info("🧠 Requesting AI Architect opinion (Gemini)...")
            results = await self._evaluate_candidates_with_ai(results, requirement)
            
        return results

    async def _evaluate_candidates_with_ai(self, candidates: List[RepoCandidate], requirement: str) -> List[RepoCandidate]:
        """
        Asks Gemini (via OpenAI-compatible endpoint) to pick the best candidate from the list.
        """
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            self.logger.warning("GEMINI_API_KEY not found. Skipping AI evaluation.")
            return candidates

        # Configurable Endpoint & Model
        # Default: Custom Proxy (OpenAI Compatible)
        base_url = os.getenv("GEMINI_BASE_URL", "https://cs.imds.ai/api/v1")
        model = os.getenv("GEMINI_MODEL", "gemini-3-pro-preview")
        
        base_url = base_url.rstrip("/")

        # Construct Prompt
        candidate_descriptions = []
        for i, c in enumerate(candidates):
            candidate_descriptions.append(
                f"Index {i}:\n"
                f"- Name: {c.name}\n"
                f"- Desc: {c.description}\n"
                f"- Stars: {c.stars}\n"
                f"- Current Score: {c.match_score}\n"
                f"- Analysis: {c.analysis_reason}"
            )
        
        system_prompt = "You are a Chief Software Architect. Help me select the best open-source repository to start a new project."
        user_prompt = (
            f"User Requirement: \"{requirement}\"\n\n"
            f"Candidates:\n" + "\n".join(candidate_descriptions) + "\n\n"
            f"Task: Select the ONE best candidate that most closely matches the requirement and is technically sound.\n"
            f"Output JSON ONLY: {{ \"selected_index\": <int>, \"reasoning\": \"<string>\" }}"
        )

        try:
            async with httpx.AsyncClient() as client:
                target_url = f"{base_url}/chat/completions"
                self.logger.info(f"🤖 Calling AI Endpoint: {target_url} (Model: {model})")
                
                resp = await client.post(
                    target_url,
                    headers={
                        "Authorization": f"Bearer {api_key}",
                        "Content-Type": "application/json"
                    },
                    json={
                        "model": model,
                        "messages": [
                            {"role": "system", "content": system_prompt},
                            {"role": "user", "content": user_prompt}
                        ],
                        "temperature": 0.2
                    },
                    timeout=30.0
                )
                
                if resp.status_code == 200:
                    data = resp.json()
                    try:
                        # OpenAI Compatible Response
                        text_resp = data["choices"][0]["message"]["content"]
                        # Clean markdown code blocks if present
                        text_resp = text_resp.replace("```json", "").replace("```", "").strip()
                        
                        decision = json.loads(text_resp)
                        idx = decision.get("selected_index")
                        ai_reason = decision.get("reasoning", "No reason provided.")
                        
                        if idx is not None and 0 <= idx < len(candidates):
                            selected = candidates[idx]
                            self.logger.info(f"🤖 AI Architect selected: {selected.name} (Index {idx})")
                            
                            # Modify the selected candidate info
                            selected.match_score += 10 # Bonus for AI selection
                            selected.analysis_reason = f"[AI SELECTED: {ai_reason}] " + selected.analysis_reason
                            
                            # Move to top
                            candidates.pop(idx)
                            candidates.insert(0, selected)
                        else:
                            self.logger.warning(f"AI returned invalid index: {idx}")
                            
                    except (KeyError, json.JSONDecodeError, IndexError) as e:
                        self.logger.error(f"Failed to parse AI response: {e}. Raw: {data}")
                else:
                    self.logger.error(f"AI API failed: {resp.status_code} - {resp.text}")
                    
        except Exception as e:
            self.logger.error(f"AI Evaluation Exception: {e}")

        return candidates

    async def _search_github_real(self, query: str) -> List[RepoCandidate]:
        """
        Performs a real search against GitHub API.
        """
        try:
            self.logger.info(f"🌐 Contacting GitHub API for: {query}")
            async with httpx.AsyncClient() as client:
                # Add 'java' to query to bias towards backend modules if not specified
                search_query = f"{query} language:java"
                resp = await client.get(
                    "https://api.github.com/search/repositories",
                    params={"q": search_query, "sort": "stars", "order": "desc", "per_page": 5},
                    headers={"Accept": "application/vnd.github.v3+json", "User-Agent": "Ingenio-Scout"}
                )
                
                if resp.status_code != 200:
                    self.logger.error(f"GitHub API Error: {resp.status_code} - {resp.text}")
                    return []
                
                data = resp.json()
                items = data.get("items", [])
                candidates = []
                
                for item in items:
                    candidates.append(RepoCandidate(
                        name=item.get("name"),
                        url=item.get("clone_url"), # Use clone_url for git operations
                        description=item.get("description") or "No description provided",
                        stars=item.get("stargazers_count", 0)
                    ))
                
                return candidates
        except Exception as e:
            self.logger.error(f"Real Search Exception: {str(e)}")
            return []

    def _mock_search_github(self, query: str) -> List[RepoCandidate]:
        """
        Simulates a Google/GitHub search result.
        """
        # Hardcoded examples to demonstrate the UI flow
        return [
            RepoCandidate(
                name="jeecg-boot-module-demo",
                url="https://github.com/jeecgboot/jeecg-boot.git",
                description="Official JeecgBoot demo module. Contains standard CRUD patterns.",
                stars=3500
            ),
            RepoCandidate(
                name="jeepay-payment-system",
                url="https://github.com/jeequan/jeepay.git",
                description="Open source payment system. High complexity, standalone app.",
                stars=1200
            ),
            RepoCandidate(
                name="stripe-java-sdk",
                url="https://github.com/stripe/stripe-java.git",
                description="Official Stripe SDK. Low level library, not a module.",
                stars=5000
            )
        ]

    async def _analyze_match(self, candidate: RepoCandidate, requirement: str) -> (int, str):
        """
        Heuristic analysis logic + Deep Read for high potential candidates.
        """
        score = 50
        reason = "Basic match."
        
        req_lower = requirement.lower()
        name_lower = candidate.name.lower()
        desc_lower = candidate.description.lower()

        if "jeecg" in req_lower and "jeecg" in name_lower:
            score += 30
            reason = "High affinity: Jeecg module pattern detected."
        
        if "stripe" in req_lower and "stripe" in name_lower:
            if "sdk" in desc_lower:
                score = 40
                reason = "Low affinity: SDK library (requires wrapping)."
            else:
                score += 40
                reason = "Good match: Payment implementation found."

        # Penalty for standalone apps if we want a module
        if "system" in desc_lower and "module" not in desc_lower:
            score -= 10
            reason += " Warning: Standalone system, migration might be complex."
            
        # --- Deep Read (Phase 7.2) ---
        if score >= 60:
            self.logger.info(f"🚀 High score ({score}) detected for {candidate.name}. Triggering Deep Read...")
            # Note: This is a synchronous call in an async method, might block briefly. 
            # In prod, offload to thread pool.
            try:
                # Assuming candidate.url is like https://github.com/user/repo.git
                files = self.template_manager.fetch_analysis_files(candidate.url)
                
                deep_insights = []
                if "pom.xml" in files:
                    deep_insights.append("Maven")
                    pom_content = files["pom.xml"]
                    if "jeecg-boot-starter" in pom_content:
                        score += 20
                        deep_insights.append("Jeecg-Native")
                        reason += " [Confirmed: Native Jeecg Dependency]"
                    if "mybatis-plus" in pom_content:
                        deep_insights.append("MyBatis+")
                
                if "package.json" in files:
                    deep_insights.append("Node")
                
                if deep_insights:
                    reason += f" (Deep Analysis: {', '.join(deep_insights)})"
                    
            except Exception as e:
                self.logger.warning(f"Deep Read failed for {candidate.name}: {e}")

        return score, reason

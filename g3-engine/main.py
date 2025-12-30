from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from typing import List, Optional, Any
import asyncio
import uuid
import logging
import os
import time

# 导入 G3 核心组件
# 注意：在 Docker 或实际运行时，PYTHONPATH 需要正确设置
try:
    from core.template_manager import TemplateManager
    from agents.repo_scout import RepoScoutAgent
except ImportError:
    # Fallback for direct execution without path setup
    import sys
    sys.path.append(os.path.dirname(os.path.abspath(__file__)))
    from core.template_manager import TemplateManager
    from agents.repo_scout import RepoScoutAgent

# 配置日志
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Ingenio G3 Engine", version="1.1.0")

# --- 初始化核心组件 ---
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
# 假设 backend 在 g3-engine 的上一级目录的兄弟目录
REGISTRY_PATH = os.path.join(BASE_DIR, "../backend/g3_context/template-registry.json")
CACHE_DIR = os.path.join(BASE_DIR, ".template_cache")

# 容错：如果找不到 registry，���录警告但不崩溃（可能在 CI 环境）
if os.path.exists(REGISTRY_PATH):
    template_manager = TemplateManager(REGISTRY_PATH, CACHE_DIR)
    scout_agent = RepoScoutAgent(template_manager)
else:
    logger.warning(f"Registry not found at {REGISTRY_PATH}. Scout capabilities will be limited.")
    template_manager = None
    scout_agent = None

# --- 数据模型 ---

class G3TaskRequest(BaseModel):
    requirement: str
    tenant_id: str
    max_rounds: int = 3
    task_type: str = "GENERATE"  # 'GENERATE' or 'SCOUT'

class G3LogEntry(BaseModel):
    timestamp: float
    role: str
    content: str
    level: str

class G3TaskResponse(BaseModel):
    task_id: str
    status: str
    message: str

class ScoutResult(BaseModel):
    candidates: List[Any]

# --- 内存存储 (MVP) ---
tasks = {}

# --- API ---

@app.post("/api/v1/g3/start", response_model=G3TaskResponse)
async def start_task(req: G3TaskRequest):
    task_id = str(uuid.uuid4())
    logger.info(f"Starting G3 Task {task_id} [{req.task_type}] for req: {req.requirement}")
    
    tasks[task_id] = {
        "id": task_id,
        "status": "RUNNING",
        "logs": [],
        "requirement": req.requirement,
        "type": req.task_type,
        "result": None
    }
    
    if req.task_type == "SCOUT":
        if not scout_agent:
            raise HTTPException(status_code=500, detail="Scout Agent not initialized")
        asyncio.create_task(run_scout_task(task_id, req.requirement))
    else:
        # Default to generation simulation
        asyncio.create_task(run_g3_simulation(task_id, req.requirement))
    
    return {
        "task_id": task_id,
        "status": "RUNNING",
        "message": f"G3 {req.task_type} Task Started"
    }

@app.get("/api/v1/g3/logs/{task_id}", response_model=List[G3LogEntry])
async def get_logs(task_id: str):
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    return tasks[task_id]["logs"]

@app.get("/api/v1/g3/result/{task_id}")
async def get_result(task_id: str):
    if task_id not in tasks:
        raise HTTPException(status_code=404, detail="Task not found")
    return tasks[task_id].get("result")

# --- 任务逻辑 ---

async def run_scout_task(task_id: str, requirement: str):
    def log(role, content, level="INFO"):
        tasks[task_id]["logs"].append({
            "timestamp": time.time() * 1000,
            "role": role,
            "content": content,
            "level": level
        })
    
    try:
        log("SYSTEM", f"Scout Agent Activated. Searching for: {requirement}")
        await asyncio.sleep(1) # UX pacing
        
        # Call the Agent
        candidates = await scout_agent.search_and_analyze(requirement)
        
        # Simulate log streaming from agent (since agent is currently synchronous/mocked)
        for cand in candidates:
            log("SCOUT", f"Analyzed {cand.name}: {cand.analysis_reason} (Score: {cand.match_score})", "INFO")
            await asyncio.sleep(0.5)

        top_pick = candidates[0] if candidates else None
        if top_pick:
             log("SCOUT", f"Recommendation: {top_pick.name} is the best match.", "SUCCESS")
        
        tasks[task_id]["result"] = [c.dict() for c in candidates]
        tasks[task_id]["status"] = "COMPLETED"
        log("SYSTEM", "Scout Mission Completed. Awaiting user selection.", "SUCCESS")

    except Exception as e:
        logger.exception("Scout failed")
        log("SYSTEM", f"Scout Error: {str(e)}", "ERROR")
        tasks[task_id]["status"] = "FAILED"

async def run_g3_simulation(task_id: str, requirement: str):
    # ... (Keep existing logic for Generate phase) ...
    def log(role, content, level="INFO"):
        tasks[task_id]["logs"].append({
            "timestamp": time.time() * 1000,
            "role": role,
            "content": content,
            "level": level
        })
    
    try:
        log("SYSTEM", f"Engine Initialized. Target: {requirement}")
        await asyncio.sleep(1)
        
        log("ARCHITECT", "Analyzing business domain...", "INFO")
        await asyncio.sleep(1.5)
        log("ARCHITECT", "Blueprint generated: [UserService, OrderService]", "SUCCESS")
        
        log("SYSTEM", "Starting Round 1", "INFO")
        
        log("PLAYER", "Generating OrderService.java...", "INFO")
        await asyncio.sleep(2)
        log("PLAYER", "Code generation complete.", "INFO")
        
        log("COACH", "Scanning for IDOR vulnerabilities...", "INFO")
        await asyncio.sleep(1)
        log("COACH", "ATTACK VECTOR FOUND: updateOrder() misses tenant check!", "ERROR")
        
        log("SYSTEM", "Round 1 Failed. Feedback sent to Player.", "ERROR")
        await asyncio.sleep(1)
        
        log("SYSTEM", "Starting Round 2", "INFO")
        log("PLAYER", "Applying security patch: Added TenantContext.getTenantId() check.", "INFO")
        await asyncio.sleep(1.5)
        
        log("COACH", "Re-running attack suite...", "INFO")
        await asyncio.sleep(1)
        log("COACH", "All tests passed.", "SUCCESS")
        
        log("SYSTEM", "G3 Process Completed. Artifacts ready.", "SUCCESS")
        tasks[task_id]["status"] = "COMPLETED"
        
    except Exception as e:
        log("SYSTEM", f"Critical Error: {str(e)}", "ERROR")
        tasks[task_id]["status"] = "FAILED"

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)
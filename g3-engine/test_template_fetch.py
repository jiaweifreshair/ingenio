import os
import sys
import logging
from pathlib import Path

# Add current directory to path so we can import 'core'
sys.path.append(os.path.dirname(os.path.abspath(__file__)))

from core.template_manager import TemplateManager, TemplateError

# Setup logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
logger = logging.getLogger("G3-Tester")

def test_fetch():
    # Paths
    project_root = Path(__file__).parent.parent
    registry_path = project_root / "backend/g3_context/template-registry.json"
    cache_dir = project_root / "g3-engine/.template_cache_test"

    logger.info(f"Registry: {registry_path}")
    
    if not registry_path.exists():
        logger.error("Registry file not found! Did you create it?")
        return

    manager = TemplateManager(str(registry_path), str(cache_dir))
    
    target_key = "jeecg-demo-local"
    
    try:
        logger.info(f"Attempting to fetch template: {target_key}")
        local_path = manager.get_template_path(target_key)
        
        logger.info(f"SUCCESS! Template located at: {local_path}")
        
        # Verify content
        expected_file = local_path / "pom.xml" 
        # Note: sub_path is 'jeecg-module-demo', so it should have a pom.xml
        
        if expected_file.exists():
            logger.info("Verification Passed: pom.xml exists.")
            # Read first few lines
            with open(expected_file, 'r') as f:
                head = [next(f) for _ in range(5)]
            logger.info(f"Content Preview:\n{''.join(head)}")
        else:
            logger.info(f"Verification Warning: pom.xml not found in {local_path}")
            # List directory to debug
            logger.info(f"Dir contents: {os.listdir(local_path)}")

    except TemplateError as e:
        logger.error(f"Template Operation Failed: {e}")
    except Exception as e:
        logger.exception(f"Unexpected Error: {e}")

if __name__ == "__main__":
    test_fetch()

import os
import json
import subprocess
import logging
from pathlib import Path
from typing import Optional, Dict, Any

# Configure logging
logger = logging.getLogger(__name__)

class TemplateError(Exception):
    """Base exception for template operations."""
    pass

class TemplateManager:
    """
    Core component for managing external code templates.
    Designed with future distributed architecture in mind:
    - Current: Local file system cache + Git CLI
    - Future: Can be backed by S3, Redis, or a centralized Artifact Repository
    """

    def __init__(self, registry_path: str, cache_dir: str = ".template_cache"):
        self.registry_path = Path(registry_path)
        self.cache_base = Path(cache_dir)
        self.registry = self._load_registry()
        
        # Ensure cache directory exists
        if not self.cache_base.exists():
            self.cache_base.mkdir(parents=True)

    def _load_registry(self) -> Dict[str, Any]:
        """Loads the registry metadata. Abstraction allowing future DB switch."""
        if not self.registry_path.exists():
            raise TemplateError(f"Registry not found at {self.registry_path}")
        
        try:
            with open(self.registry_path, 'r', encoding='utf-8') as f:
                data = json.load(f)
                # Convert list to dict for O(1) lookup
                return {t['key']: t for t in data.get('templates', [])}
        except Exception as e:
            raise TemplateError(f"Failed to load registry: {str(e)}")

    def get_template_path(self, template_key: str, force_update: bool = False) -> Path:
        """
        Public API: Returns the local file system path for a requested template.
        Triggers fetch/update if necessary.
        """
        if template_key not in self.registry:
            raise TemplateError(f"Template '{template_key}' not defined in registry.")

        template_meta = self.registry[template_key]
        cache_path = self.cache_base / template_key

        if template_meta.get('type') != 'git':
            raise TemplateError(f"Unsupported template type: {template_meta.get('type')}")

        # Check if exists
        if cache_path.exists():
            if force_update:
                logger.info(f"Force updating template: {template_key}")
                self._update_git_repo(cache_path, template_meta)
            else:
                logger.debug(f"Cache hit for template: {template_key}")
        else:
            logger.info(f"Downloading template: {template_key}")
            self._fetch_git_repo(cache_path, template_meta)

        # Return specific sub-path if requested
        sub_path = template_meta.get('sub_path')
        if sub_path:
            target_path = cache_path / sub_path
            if not target_path.exists():
                raise TemplateError(f"Sub-path '{sub_path}' not found in repo for {template_key}")
            return target_path
        
        return cache_path

    def fetch_analysis_files(self, repo_url: str, branch: str = "HEAD") -> Dict[str, str]:
        """
        Ad-hoc fetching of key analysis files for the Scout Agent.
        Does not use the cache.
        """
        import tempfile
        import shutil

        # Files to look for
        target_files = ["pom.xml", "package.json", "build.gradle", "README.md"]
        
        with tempfile.TemporaryDirectory() as temp_dir:
            temp_path = Path(temp_dir)
            try:
                # 1. Initialize
                self._run_git(['init'], cwd=temp_path)
                self._run_git(['remote', 'add', 'origin', repo_url], cwd=temp_path)
                
                # 2. Sparse checkout config
                self._run_git(['config', 'core.sparseCheckout', 'true'], cwd=temp_path)
                sparse_info = temp_path / '.git' / 'info' / 'sparse-checkout'
                with open(sparse_info, 'w') as f:
                    for tf in target_files:
                        f.write(f"{tf}\n")

                # 3. Pull (Try main first, then master if HEAD is ambiguous, but usually HEAD works if we don't specify branch in pull, wait, remote HEAD needs fetch)
                # Let's try to detect default branch or just try pulling HEAD
                # For safety, let's fetch shallowly
                self._run_git(['pull', '--depth', '1', 'origin', branch], cwd=temp_path)
                
                # 4. Read files
                results = {}
                for filename in target_files:
                    fpath = temp_path / filename
                    if fpath.exists():
                        try:
                            results[filename] = fpath.read_text(encoding='utf-8', errors='ignore')
                        except Exception:
                            pass
                
                return results

            except TemplateError as e:
                # If 'HEAD' fails, maybe try 'master' or 'main' explicitly if strictly needed, 
                # but usually failure here means repo doesn't exist or private.
                logger.warning(f"Failed to fetch analysis files from {repo_url}: {e}")
                return {}
            except Exception as e:
                logger.error(f"Unexpected error fetching analysis files: {e}")
                return {}

    def _run_git(self, args: list, cwd: Optional[Path] = None):
        """Internal helper to execute git commands safely."""
        try:
            result = subprocess.run(
                ['git'] + args,
                cwd=str(cwd) if cwd else None,
                check=True,
                stdout=subprocess.PIPE,
                stderr=subprocess.PIPE,
                text=True
            )
            return result.stdout.strip()
        except subprocess.CalledProcessError as e:
            raise TemplateError(f"Git command failed: {' '.join(args)}\nError: {e.stderr}")

    def _fetch_git_repo(self, target_dir: Path, meta: Dict):
        """Clones a repository (supports sparse checkout for efficiency)."""
        repo_url = meta['repository']
        branch = meta.get('branch', 'master')
        sub_path = meta.get('sub_path')

        # 1. Create directory
        target_dir.mkdir(parents=True, exist_ok=True)

        # 2. Init and Config
        self._run_git(['init'], cwd=target_dir)
        self._run_git(['remote', 'add', 'origin', repo_url], cwd=target_dir)
        
        # 3. Sparse Checkout Setup (if sub_path is defined)
        if sub_path:
            self._run_git(['config', 'core.sparseCheckout', 'true'], cwd=target_dir)
            sparse_info_path = target_dir / '.git' / 'info' / 'sparse-checkout'
            with open(sparse_info_path, 'w') as f:
                f.write(f"{sub_path}\n")
        
        # 4. Pull
        try:
            logger.info(f"Pulling {branch} from {repo_url}...")
            self._run_git(['pull', '--depth', '1', 'origin', branch], cwd=target_dir)
        except TemplateError as e:
            # Cleanup on failure to prevent corrupted cache
            import shutil
            shutil.rmtree(target_dir)
            raise e

    def _update_git_repo(self, target_dir: Path, meta: Dict):
        """Updates an existing repository."""
        branch = meta.get('branch', 'master')
        self._run_git(['fetch', '--depth', '1', 'origin', branch], cwd=target_dir)
        self._run_git(['reset', '--hard', f'origin/{branch}'], cwd=target_dir)

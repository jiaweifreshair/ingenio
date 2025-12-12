#!/bin/bash

# Start Agent Service
cd "$(dirname "$0")/../agent-service"

echo "🚀 Starting Agent Service..."

if [ ! -d "venv" ]; then
    echo "Creating virtual environment..."
    python3 -m venv venv
fi

source venv/bin/activate

echo "📦 Installing dependencies..."
pip install -r requirements.txt

echo "🐍 Starting FastAPI server..."
# Add src to PYTHONPATH so imports work
export PYTHONPATH=$PYTHONPATH:$(pwd)
python3 -m uvicorn src.api.main:app --host 0.0.0.0 --port 8000 --reload

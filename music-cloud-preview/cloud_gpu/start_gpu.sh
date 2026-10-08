#!/usr/bin/env bash
set -euo pipefail
: "${ACESTEP_API_KEY:?Set ACESTEP_API_KEY to a strong secret before launching}"
export ACESTEP_API_HOST=0.0.0.0
export ACESTEP_API_PORT="${ACESTEP_API_PORT:-8001}"
export ACESTEP_CONFIG_PATH="${ACESTEP_CONFIG_PATH:-acestep-v15-xl-turbo}"
export ACESTEP_LM_MODEL_PATH="${ACESTEP_LM_MODEL_PATH:-acestep-5Hz-lm-1.7B}"
export ACESTEP_INIT_LLM=true
# Requires preinstalled Python 3.11/3.12, uv and working NVIDIA GPU drivers.
if [ ! -d ACE-Step-1.5 ]; then
  git clone https://github.com/ace-step/ACE-Step-1.5.git
fi
cd ACE-Step-1.5
uv sync
exec uv run acestep-api

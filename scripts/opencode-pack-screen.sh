#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 1 ]]; then
  echo "Usage: $0 <path-to-Fragment-or-Activity.kt>"
  exit 1
fi

TARGET="$1"
if [[ ! -f "$TARGET" ]]; then
  echo "File not found: $TARGET"
  exit 1
fi

DIR="$(dirname "$TARGET")"
BASE="$(basename "$TARGET" .kt)"

VM_FILE="$(find "$DIR" -maxdepth 2 -name "*ViewModel.kt" | head -n 1 || true)"
XML_GUESS="$(rg -n "Binding\.inflate|R\.layout\." "$TARGET" | head -n 3 || true)"

cat <<EOF
Port \`$BASE\` to Jetpack Compose.

Context files to read ONLY:
1. \`docs/compose-port/COMPOSE_REFERENCE.md\`
2. \`$TARGET\`
${VM_FILE:+3. \`$VM_FILE\`}

Detected layout bindings in \`$BASE\`:
${XML_GUESS:-"(none detected)"}

Rules:
- Keep all existing ViewModel logic, Hilt injection, and AppRouter navigation intact.
- Host Compose inside the existing Fragment via \`ComposeView\` (\`DisposeOnViewTreeLifecycleDestroyed\`) + \`KurohaTheme { ... }\`, or \`ComponentActivity.setContent { KurohaTheme { ... } }\`.
- Use Material 3 (\`androidx.compose.material3.*\`) and \`coil3.compose.AsyncImage\`.
- Handle edge-to-edge insets (\`Scaffold\` padding or \`WindowInsets\`).
- Do NOT invent \`R.string.*\`, \`R.drawable.*\`, or helper functions — verify with \`rg\` first.
- After editing, run \`./scripts/opencode-verify.sh\` and fix all \`e:\` errors until exit code is 0.
EOF
